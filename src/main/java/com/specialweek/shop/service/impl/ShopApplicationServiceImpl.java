package com.specialweek.shop.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.specialweek.audit.domain.AuditRecord;
import com.specialweek.audit.mapper.AuditRecordMapper;
import com.specialweek.common.util.UserRoles;
import com.specialweek.common.web.Result;
import com.specialweek.shop.api.dto.ShopApplyRequest;
import com.specialweek.shop.api.dto.ShopAuditRequest;
import com.specialweek.shop.domain.Shop;
import com.specialweek.shop.domain.ShopApplication;
import com.specialweek.shop.mapper.ShopApplicationMapper;
import com.specialweek.shop.mapper.ShopMapper;
import com.specialweek.shop.service.IShopTypeService;
import com.specialweek.shop.service.ShopApplicationService;
import com.specialweek.storage.OssStorageService;
import com.specialweek.user.domain.User;
import com.specialweek.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.specialweek.common.util.RedisConstants.CACHE_SHOP_KEY;

@Service
@RequiredArgsConstructor
public class ShopApplicationServiceImpl implements ShopApplicationService {

    private final ShopApplicationMapper applicationMapper;
    private final ShopMapper shopMapper;
    private final IShopTypeService shopTypeService;
    private final AuditRecordMapper auditRecordMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final OssStorageService ossStorageService;

    @Override
    @Transactional
    public Result apply(ShopApplyRequest request, long userId) {
        String lockKey = "lock:submit:shop:" + userId;
        if (!BooleanUtil.isTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS))) {
            return Result.fail("请勿重复提交");
        }
        if (shopTypeService.getById(request.typeId()) == null) {
            return Result.fail("商铺类型不存在");
        }
        Long ownShopCount = shopMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shop>()
                        .eq(Shop::getOwnerUserId, userId));
        if (ownShopCount != null && ownShopCount > 0) {
            return Result.fail("一个账号只能拥有一家店铺");
        }
        Long pendingCount = applicationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopApplication>()
                        .eq(ShopApplication::getApplicantUserId, userId)
                        .in(ShopApplication::getAuditStatus, 0, 1));
        if (pendingCount != null && pendingCount > 0) {
            return Result.fail("已有进行中的商铺申请，请勿重复提交");
        }
        ShopApplication app = new ShopApplication()
                .setApplyType(0)
                .setApplicantUserId(userId)
                .setName(request.name().trim())
                .setTypeId(request.typeId())
                .setImages(
                ossStorageService.promoteApplicationImages(
                        request.imageKeys(),
                        userId,
                        "shop"
                )
                )
                .setArea(request.area())
                .setAddress(request.address().trim())
                .setAvgPrice(request.avgPrice())
                .setOpenHours(request.openHours())
                .setAuditStatus(0);
        applicationMapper.insert(app);
        return Result.ok(app.getId());
    }

    @Override
    @Transactional
    public Result applyUpdate(long shopId, ShopApplyRequest request, long userId, int role) {
        String lockKey = "lock:submit:shopupdate:" + userId + ":" + shopId;
        if (!BooleanUtil.isTrue(stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS))) {
            return Result.fail("请勿重复提交");
        }
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || !Integer.valueOf(1).equals(shop.getBusinessStatus())) {
            return Result.fail("店铺不存在或未营业");
        }
        if (role != UserRoles.ADMIN && !Objects.equals(shop.getOwnerUserId(), userId)) {
            return Result.fail("无权操作该店铺");
        }
        if (StrUtil.isBlank(request.name()) || request.typeId() == null || StrUtil.isBlank(request.address())) {
            return Result.fail("商铺名称、类型和地址不能为空");
        }
        if (shopTypeService.getById(request.typeId()) == null) {
            return Result.fail("商铺类型不存在");
        }

        Long pendingCount = applicationMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopApplication>()
                        .eq(ShopApplication::getTargetShopId, shopId)
                        .eq(ShopApplication::getApplyType, 1)
                        .eq(ShopApplication::getAuditStatus, 0));
        if (pendingCount != null && pendingCount > 0) {
            return Result.fail("该店铺已有资料修改申请在审核中");
        }

        String images = resolveUpdateImages(
                request.imageKeys(),
                shop.getImages(),
                userId,
                "shop"
        );

        ShopApplication app = new ShopApplication()
                .setApplyType(1)
                .setApplicantUserId(userId)
                .setTargetShopId(shopId)
                .setName(request.name().trim())
                .setTypeId(request.typeId())
                .setImages(images)
                .setArea(request.area())
                .setAddress(request.address().trim())
                .setAvgPrice(request.avgPrice())
                .setOpenHours(request.openHours())
                .setAuditStatus(0);
        applicationMapper.insert(app);
        return Result.ok(app.getId());
    }

    @Override
    public Result mine(long userId, int current, int size) {
        Page<ShopApplication> page = applicationMapper.selectPage(
                new Page<>(current, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopApplication>()
                        .eq(ShopApplication::getApplicantUserId, userId)
                        .orderByDesc(ShopApplication::getCreateTime));
        return Result.ok(page.getRecords());
    }

    /**
     * 更新申请
     * @param id
     * @param request
     * @param userId
     * @return
     */
    @Override
    @Transactional
    public Result update(long id, ShopApplyRequest request, long userId) {
        ShopApplication app = applicationMapper.selectById(id);
        if (app == null) {
            return Result.fail("申请不存在");
        }
        if (!app.getApplicantUserId().equals(userId)) {
            return Result.fail("无权修改该申请");
        }
        if (app.getAuditStatus() != null && app.getAuditStatus() == 1) {
            return Result.fail("已通过的申请不能修改");
        }
        if (shopTypeService.getById(request.typeId()) == null) {
            return Result.fail("商铺类型不存在");
        }
        app.setName(request.name().trim())
                .setTypeId(request.typeId())
                .setImages(
                        resolveUpdateImages(
                                request.imageKeys(),
                                app.getImages(),
                                userId,
                                "shop"
                        )
                )
                .setArea(request.area())
                .setAddress(request.address().trim())
                .setAvgPrice(request.avgPrice())
                .setOpenHours(request.openHours())
                .setAuditStatus(0)
                .setRejectReason(null);
        applicationMapper.updateById(app);
        return Result.ok();
    }

    @Override
    public Result listForAdmin(Integer applyType, Integer auditStatus, int current, int size) {
        Page<ShopApplication> page = applicationMapper.selectPage(
                new Page<>(current, size),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShopApplication>()
                        .eq(applyType != null, ShopApplication::getApplyType, applyType)
                        .eq(auditStatus != null, ShopApplication::getAuditStatus, auditStatus)
                        .orderByDesc(ShopApplication::getCreateTime));
        return Result.ok(page.getRecords());
    }

    /**
     * 审核开店或商铺资料修改申请并保存审核结果
     * @param id
     * @param request
     * @param adminId
     * @return
     */
    @Override
    @Transactional
    public Result audit(long id, ShopAuditRequest request, long adminId) {
        ShopApplication app = applicationMapper.selectById(id);
        if (app == null || !Integer.valueOf(0).equals(app.getAuditStatus())) {
            return Result.fail("申请不存在或已处理");
        }
        boolean modify = app.getApplyType() != null && app.getApplyType() == 1;
        if (!request.approved()) {
            if (StrUtil.isBlank(request.reason())) {
                return Result.fail("拒绝时必须填写原因");
            }
            applicationMapper.reject(id, adminId, request.reason());
            auditRecordMapper.insert(new AuditRecord()
                    .setTargetType(modify ? "SHOP_UPDATE" : "SHOP")
                    .setTargetId(id)
                    .setFromStatus(0)
                    .setToStatus(2)
                    .setOperatorUserId(adminId)
                    .setReason(request.reason()));
            return Result.ok();
        }
        if (modify) {
            return auditModify(app, request, adminId);
        }
        if (request.x() == null || request.y() == null) {
            return Result.fail("审核通过时必须补齐经纬度");
        }
        Long ownShopCount = shopMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shop>()
                        .eq(Shop::getOwnerUserId, app.getApplicantUserId()));
        if (ownShopCount != null && ownShopCount > 0) {
            applicationMapper.reject(id, adminId, "申请人已拥有店铺");
            auditRecordMapper.insert(new AuditRecord()
                    .setTargetType("SHOP")
                    .setTargetId(id)
                    .setFromStatus(0)
                    .setToStatus(2)
                    .setOperatorUserId(adminId)
                    .setReason("申请人已拥有店铺"));
            return Result.fail("该申请人已拥有店铺，申请已自动拒绝");
        }
        String images = app.getImages();
        Shop shop = new Shop()
                .setOwnerUserId(app.getApplicantUserId())
                .setName(app.getName())
                .setTypeId(app.getTypeId())
                .setImages(images)
                .setArea(app.getArea())
                .setAddress(app.getAddress())
                .setX(request.x())
                .setY(request.y())
                .setAvgPrice(app.getAvgPrice())
                .setOpenHours(app.getOpenHours())
                .setSold(0)
                .setComments(0)
                .setScore(0)
                .setBusinessStatus(1);
        shopMapper.insert(shop);
        int changed = applicationMapper.approve(
                id,
                adminId,
                shop.getId(),
                request.x(),
                request.y()
        );

        if (changed != 1) {
            throw new IllegalStateException("申请状态更新失败");
        }
        auditRecordMapper.insert(new AuditRecord()
                .setTargetType("SHOP")
                .setTargetId(id)
                .setFromStatus(0)
                .setToStatus(1)
                .setOperatorUserId(adminId)
                .setReason(request.reason()));
        promoteApplicantToMerchant(app.getApplicantUserId());
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok(shop.getId());
    }

    /**
     * 将商铺资料修改申请应用到商铺并保存审核结果和正式图片地址
     * @param app
     * @param request
     * @param adminId
     * @return
     */
    private Result auditModify(ShopApplication app, ShopAuditRequest request, long adminId) {
        Shop shop = shopMapper.selectById(app.getTargetShopId());
        if (shop == null) {
            return Result.fail("目标店铺不存在");
        }
        String images = app.getImages();

        shop.setName(app.getName())
                .setTypeId(app.getTypeId())
                .setImages(images)
                .setArea(app.getArea())
                .setAddress(app.getAddress())
                .setAvgPrice(app.getAvgPrice())
                .setOpenHours(app.getOpenHours());
        if (request.x() != null && request.y() != null) {
            shop.setX(request.x());
            shop.setY(request.y());
        }
        shopMapper.updateById(shop);
        int changed = applicationMapper.approve(
                app.getId(),
                adminId,
                shop.getId(),
                request.x(),
                request.y()
        );
        if (changed != 1) {
            throw new IllegalStateException("申请状态更新失败");
        }
        auditRecordMapper.insert(new AuditRecord()
                .setTargetType("SHOP_UPDATE")
                .setTargetId(app.getId())
                .setFromStatus(0)
                .setToStatus(1)
                .setOperatorUserId(adminId)
                .setReason(request.reason()));
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok(shop.getId());
    }

    private void promoteApplicantToMerchant(Long applicantUserId) {
        User applicant = userMapper.selectById(applicantUserId);
        if (applicant == null) {
            return;
        }
        int role = applicant.getRole() == null ? UserRoles.USER : applicant.getRole();
        if (role < UserRoles.MERCHANT) {
            applicant.setRole(UserRoles.MERCHANT);
            userMapper.updateById(applicant);
        }
    }

    /**
     * 用户修改申请时图片到底怎么处理
     * 三种情况
     */
    private String resolveUpdateImages(
            List<String> imageKeys,
            String oldImages,
            long userId,
            String scene
    ) {
        /*
         * imageKeys == null：
         * 表示前端没有修改图片，保留原来的正式图片 URL。
         */
        if (imageKeys == null) {
            return StrUtil.nullToEmpty(oldImages);
        }

        /*
         * imageKeys 为空集合：
         * 表示用户主动清空图片。
         */
        return ossStorageService.promoteApplicationImages(
                imageKeys,
                userId,
                scene
        );
    }

}
