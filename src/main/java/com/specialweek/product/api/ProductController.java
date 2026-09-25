package com.specialweek.product.api;

import com.specialweek.common.util.SystemConstants;
import com.specialweek.common.web.Result;
import com.specialweek.counter.service.CounterService;
import com.specialweek.product.api.dto.ProductFavoriteActionResponse;
import com.specialweek.product.api.dto.ProductDetailResponse;
import com.specialweek.product.domain.Product;
import com.specialweek.product.mapper.ProductMapper;
import com.specialweek.product.service.ProductFeedService;
import com.specialweek.product.service.ProductDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductMapper productMapper;
    private final ProductFeedService productFeedService;
    private final ProductDetailService productDetailService;
    private final CounterService counterService;

    /**
     * 返回雀探商品分页。
     * @param current
     * @param jwt
     * @return
     */
    @GetMapping("/feed")
    public Result feed(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @AuthenticationPrincipal Jwt jwt) {
        Long uid = jwt == null ? null : Long.parseLong(jwt.getSubject());
        return Result.ok(productFeedService.publicFeed(
                current, SystemConstants.MAX_PAGE_SIZE, uid));
    }

    /**
     * 返回指定商铺的商品列表。
     * @param shopId
     * @return
     */
    @GetMapping("/list/{shopId}")
    public Result listOfShop(@PathVariable long shopId) {
        List<Product> products = productMapper.queryPublicByShop(shopId);
        return Result.ok(products);
    }

    /**
     * 读取商品和商铺基础详情并补充实时收藏信息。
     * @param id
     * @param jwt
     * @return
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable long id,
                         @AuthenticationPrincipal Jwt jwt) {
        Long uid = jwt == null ? null : Long.parseLong(jwt.getSubject());
        ProductDetailResponse detail = productDetailService.detail(id, uid);
        return detail == null
                ? Result.fail("商品不存在或已下架")
                : Result.ok(detail);
    }

    /**
     * 收藏公开商品并返回操作结果和实时计数。
     * @param id
     * @param jwt
     * @return
     */
    @PutMapping("/{id}/favorite")
    public Result favorite(@PathVariable long id,
                           @AuthenticationPrincipal Jwt jwt) {
        requirePublished(id);
        long uid = Long.parseLong(jwt.getSubject());
        boolean changed = counterService.fav("product", String.valueOf(id), uid);
        long count = counterService.getCounts(
                "product", String.valueOf(id), List.of("fav"))
                .getOrDefault("fav", 0L);
        return Result.ok(new ProductFavoriteActionResponse(
                id, true, changed, count));
    }

    /**
     * 取消商品收藏并返回操作结果和实时计数。
     * @param id
     * @param jwt
     * @return
     */
    @DeleteMapping("/{id}/favorite")
    public Result unfavorite(@PathVariable long id,
                             @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getSubject());
        boolean changed = counterService.unfav("product", String.valueOf(id), uid);
        long count = counterService.getCounts(
                "product", String.valueOf(id), List.of("fav"))
                .getOrDefault("fav", 0L);
        return Result.ok(new ProductFavoriteActionResponse(
                id, false, changed, count));
    }

    /**
     * 按用户收藏顺序读取版本化缓存并补充实时收藏状态。
     * @param current
     * @param jwt
     * @return
     */
    @GetMapping("/of/myfavs")
    public Result myFavorites(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getSubject());
        return Result.ok(productFeedService.myFavorites(
                uid, current, SystemConstants.MAX_PAGE_SIZE));
    }

    /**
     * 校验商品已上架且所属商铺正在营业。
     * @param id
     */
    private void requirePublished(long id) {
        if (productMapper.selectPublicDetail(id) == null) {
            throw new IllegalArgumentException("商品不存在或已下架");
        }
    }
}
