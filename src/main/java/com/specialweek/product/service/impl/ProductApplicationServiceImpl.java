package com.specialweek.product.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.audit.domain.AuditRecord;
import com.specialweek.audit.mapper.AuditRecordMapper;
import com.specialweek.common.util.UserRoles;
import com.specialweek.common.web.Result;
import com.specialweek.product.api.dto.ProductApplyRequest;
import com.specialweek.product.api.dto.ProductAuditRequest;
import com.specialweek.product.domain.Product;
import com.specialweek.product.domain.ProductApplication;
import com.specialweek.product.mapper.ProductApplicationMapper;
import com.specialweek.product.mapper.ProductMapper;
import com.specialweek.product.service.ProductApplicationService;
import com.specialweek.product.service.ProductFeedService;
import com.specialweek.product.service.ProductDetailService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.specialweek.shop.domain.Shop;
import com.specialweek.shop.mapper.ShopMapper;
import com.specialweek.storage.OssStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.specialweek.common.util.RedisConstants.CACHE_PRODUCT_LIST_KEY;

@Service
@RequiredArgsConstructor
public class ProductApplicationServiceImpl implements ProductApplicationService {

    private final ProductApplicationMapper applicationMapper;
    private final ProductMapper productMapper;
    private final ProductFeedService productFeedService;
    private final ProductDetailService productDetailService;
    private final ShopMapper shopMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final OssStorageService ossStorageService;

    @Override
    @Transactional
    public Result apply(long shopId, ProductApplyRequest request, long userId, int role) {
        String lockKey = "lock:submit:product:" + userId + ":" + shopId;
        if (!BooleanUtil.isTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS))) {
            return Result.fail("请勿重复提交");
        }
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || !Integer.valueOf(1).equals(shop.getBusinessStatus())) {
            return Result.fail("店铺不存在或未营业");
        }
        if (role != UserRoles.ADMIN && !Objects.equals(shop.getOwnerUserId(), userId)) {
            return Result.fail("无权在该商铺发布商品");
        }
        ProductApplication app = new ProductApplication()
                .setApplicantUserId(userId)
                .setShopId(shopId)
                .setName(request.name().trim())
                .setDescription(request.description())
                .setImages(ossStorageService.promoteApplicationImages(
                        request.imageKeys(),
                        userId,
                        "product"
                ))
                .setPrice(request.price())
                .setStock(request.stock())
                .setAuditStatus(0);
        applicationMapper.insert(app);
        return Result.ok(app.getId());
    }

    @Override
    public Result mine(long shopId, long userId, int current, int size) {
        Page<ProductApplication> page = applicationMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ProductApplication>()
                        .eq(ProductApplication::getShopId, shopId)
                        .eq(ProductApplication::getApplicantUserId, userId)
                        .orderByDesc(ProductApplication::getCreateTime));
        return Result.ok(page.getRecords());
    }

    @Override
    @Transactional
    public Result update(long id, ProductApplyRequest request, long userId) {
        ProductApplication app = applicationMapper.selectById(id);
        if (app == null) {
            return Result.fail("申请不存在");
        }
        if (!app.getApplicantUserId().equals(userId)) {
            return Result.fail("无权修改该申请");
        }
        if (app.getAuditStatus() != null && app.getAuditStatus() == 1) {
            return Result.fail("已通过的申请不能修改");
        }
        app.setName(request.name().trim())
                .setDescription(request.description())
                .setImages(resolveUpdateImages(
                        request.imageKeys(),
                        app.getImages(),
                        userId
                ))
                .setPrice(request.price())
                .setStock(request.stock())
                .setAuditStatus(0)
                .setRejectReason(null);
        applicationMapper.updateById(app);
        return Result.ok();
    }

    @Override
    public Result listForAdmin(Integer auditStatus, int current, int size) {
        Page<ProductApplication> page = applicationMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ProductApplication>()
                        .eq(auditStatus != null, ProductApplication::getAuditStatus, auditStatus)
                        .orderByDesc(ProductApplication::getCreateTime));
        return Result.ok(page.getRecords());
    }

    /**
     * 审核商品申请并在通过时创建正式商品和保存审核结果
     * @param id
     * @param request
     * @param adminId
     * @return
     */
    @Override
    @Transactional
    public Result audit(long id, ProductAuditRequest request, long adminId) {
        ProductApplication app = applicationMapper.selectById(id);
        if (app == null || !Integer.valueOf(0).equals(app.getAuditStatus())) {
            return Result.fail("商品申请不存在或已处理");
        }
        if (!request.approved()) {
            if (StrUtil.isBlank(request.reason())) {
                return Result.fail("拒绝时必须填写原因");
            }
            int changed = applicationMapper.reject(id, adminId, request.reason());
            if (changed != 1) {
                return Result.fail("申请已被其他管理员处理");
            }
            auditRecordMapper.insert(new AuditRecord()
                    .setTargetType("PRODUCT")
                    .setTargetId(id)
                    .setFromStatus(0)
                    .setToStatus(2)
                    .setOperatorUserId(adminId)
                    .setReason(request.reason()));
            return Result.ok();
        }

        String images = app.getImages();
        Product product = new Product()
                .setShopId(app.getShopId())
                .setCreatedBy(app.getApplicantUserId())
                .setName(app.getName())
                .setDescription(app.getDescription())
                .setImages(images)
                .setPrice(app.getPrice())
                .setStock(app.getStock())
                .setStatus(2);
        productMapper.insert(product);
        int changed = applicationMapper.approve(id, adminId, product.getId());
        if (changed != 1) {
            throw new IllegalStateException("申请状态更新失败");
        }
        auditRecordMapper.insert(new AuditRecord()
                .setTargetType("PRODUCT")
                .setTargetId(id)
                .setFromStatus(0)
                .setToStatus(1)
                .setOperatorUserId(adminId)
                .setReason(request.reason()));
        stringRedisTemplate.delete(CACHE_PRODUCT_LIST_KEY + app.getShopId());
        return Result.ok(product.getId());
    }

    @Override
    public Result listOfShop(long shopId, long userId, int role) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || !Integer.valueOf(1).equals(shop.getBusinessStatus())) {
            return Result.fail("店铺不存在或未营业");
        }
        if (role != UserRoles.ADMIN && !Objects.equals(shop.getOwnerUserId(), userId)) {
            return Result.fail("无权操作该店铺");
        }
        return Result.ok(productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getShopId, shopId)
                .orderByDesc(Product::getCreateTime)));
    }

    /**
     * 校验商铺权限并变更商品上下架状态及失效缓存。
     * @param id
     * @param status
     * @param userId
     * @param role
     * @return
     */
    @Override
    @Transactional
    public Result changeStatus(long id, int status, long userId, int role) {
        if (status != 1 && status != 2) {
            return Result.fail("只允许上架(1)或下架(2)");
        }
        Product product = productMapper.selectById(id);
        if (product == null) {
            return Result.fail("商品不存在");
        }
        Shop shop = shopMapper.selectById(product.getShopId());
        if (shop == null || (role != UserRoles.ADMIN && !Objects.equals(shop.getOwnerUserId(), userId))) {
            return Result.fail("无权操作该商品");
        }
        boolean wasPublished = Integer.valueOf(1).equals(product.getStatus());
        invalidateProductCaches(product, wasPublished);
        product.setStatus(status);
        productMapper.updateById(product);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 在事务提交后清除商品缓存。
             */
            @Override
            public void afterCommit() {
                invalidateProductCaches(product, wasPublished);
            }
        });
        return Result.ok();
    }
    /**
     * 清除商品详情、原有公共页面和商铺商品列表缓存。
     * @param product
     * @param wasPublished
     */
    private void invalidateProductCaches(Product product, boolean wasPublished) {
        if (wasPublished) {
            productFeedService.invalidateProduct(product.getId());
        }
        productDetailService.invalidate(product.getId());
        stringRedisTemplate.delete(CACHE_PRODUCT_LIST_KEY + product.getShopId());
    }

    private String resolveUpdateImages(
            List<String> imageKeys,
            String oldImages,
            long userId
    ) {
        if (imageKeys == null) {
            return StrUtil.nullToEmpty(oldImages);
        }
        return ossStorageService.promoteApplicationImages(
                imageKeys,
                userId,
                "product"
        );
    }
}
