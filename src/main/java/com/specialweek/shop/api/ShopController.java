package com.specialweek.shop.api;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.common.web.Result;
import com.specialweek.shop.api.dto.ShopApplyRequest;
import com.specialweek.shop.domain.Shop;
import com.specialweek.shop.service.IShopService;
import com.specialweek.shop.service.ShopApplicationService;
import com.specialweek.common.util.SystemConstants;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    @Resource
    public ShopApplicationService shopApplicationService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 更新商铺信息：提交商铺资料修改申请，管理员审核通过后才会覆盖正式店铺资料
     * @param shopId 店铺id
     * @param request 新资料
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestParam("id") Long shopId,
                             @Valid @RequestBody ShopApplyRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        Long userId  = Long.parseLong(jwt.getSubject());
        if (shopId == null) {
            return Result.fail("店铺id不能为空");
        }
        Object roleClaim = jwt.getClaim("role");
        int role = roleClaim instanceof Number number ? number.intValue() : 0;
        return shopApplicationService.applyUpdate(shopId, request, userId, role);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .eq("business_status", 1)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .eq("business_status", 1)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
