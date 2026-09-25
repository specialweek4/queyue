package com.specialweek.voucher.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.specialweek.common.util.UserRoles;
import com.specialweek.common.web.Result;
import com.specialweek.product.domain.Product;
import com.specialweek.product.mapper.ProductMapper;
import com.specialweek.shop.domain.Shop;
import com.specialweek.shop.mapper.ShopMapper;
import com.specialweek.voucher.api.dto.SeckillVoucherCreateRequest;
import com.specialweek.voucher.api.dto.VoucherCreateRequest;
import com.specialweek.voucher.domain.SeckillVoucher;
import com.specialweek.voucher.domain.Voucher;
import com.specialweek.voucher.mapper.SeckillVoucherMapper;
import com.specialweek.voucher.mapper.VoucherMapper;
import com.specialweek.voucher.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.specialweek.common.util.RedisConstants.CACHE_VOUCHER_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ProductMapper productMapper;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public Result createNormal(long shopId, VoucherCreateRequest request, long userId, int role) {
        String lockKey = "lock:submit:voucher:" + userId + ":" + shopId;
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
        if (request.actualValue() < request.payValue()) {
            return Result.fail("抵扣金额必须大于等于支付金额");
        }
        if (request.productId() != null) {
            Product product = productMapper.selectById(request.productId());
            if (product == null || !Objects.equals(product.getShopId(), shopId)) {
                return Result.fail("商品不属于该店铺");
            }
            if (!Integer.valueOf(1).equals(product.getStatus())) {
                return Result.fail("商品尚未上架");
            }
        }
        Voucher voucher = new Voucher()
                .setShopId(shopId)
                .setCreatedBy(userId)
                .setProductId(request.productId())
                .setTitle(request.title().trim())
                .setSubTitle(request.subTitle())
                .setRules(request.rules())
                .setPayValue(request.payValue())
                .setActualValue(request.actualValue())
                .setType(0)
                .setStatus(2);
        save(voucher);
        stringRedisTemplate.delete(CACHE_VOUCHER_LIST_KEY + shopId);
        return Result.ok(voucher.getId());
    }

    @Override
    @Transactional
    public Result createSeckill(long shopId, SeckillVoucherCreateRequest request, long userId, int role) {
        String lockKey = "lock:submit:seckill:" + userId + ":" + shopId;
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
        if (request.actualValue() < request.payValue()) {
            return Result.fail("抵扣金额必须大于等于支付金额");
        }
        if (!request.beginTime().isBefore(request.endTime())) {
            return Result.fail("开始时间必须早于结束时间");
        }
        if (request.productId() != null) {
            Product product = productMapper.selectById(request.productId());
            if (product == null || !Objects.equals(product.getShopId(), shopId)) {
                return Result.fail("商品不属于该店铺");
            }
        }
        Voucher voucher = new Voucher()
                .setShopId(shopId)
                .setCreatedBy(userId)
                .setProductId(request.productId())
                .setTitle(request.title().trim())
                .setSubTitle(request.subTitle())
                .setRules(request.rules())
                .setPayValue(request.payValue())
                .setActualValue(request.actualValue())
                .setType(1)
                .setStatus(2);
        save(voucher);

        SeckillVoucher seckill = new SeckillVoucher()
                .setVoucherId(voucher.getId())
                .setStock(request.stock())
                .setBeginTime(request.beginTime())
                .setEndTime(request.endTime());
        seckillVoucherMapper.insert(seckill);

        stringRedisTemplate.delete(CACHE_VOUCHER_LIST_KEY + shopId);
        return Result.ok(voucher.getId());
    }

    @Override
    public Result listMine(long shopId, long userId, int role) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null || !Integer.valueOf(1).equals(shop.getBusinessStatus())) {
            return Result.fail("店铺不存在或未营业");
        }
        if (role != UserRoles.ADMIN && !Objects.equals(shop.getOwnerUserId(), userId)) {
            return Result.fail("无权操作该店铺");
        }
        List<Voucher> vouchers = list(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getShopId, shopId)
                .orderByDesc(Voucher::getCreateTime));
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public Result changeStatus(long voucherId, int status, long userId, int role) {
        if (status != 1 && status != 2) {
            return Result.fail("只允许上架(1)或下架(2)");
        }
        Voucher voucher = getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        Shop shop = shopMapper.selectById(voucher.getShopId());
        if (shop == null || (role != UserRoles.ADMIN && !Objects.equals(shop.getOwnerUserId(), userId))) {
            return Result.fail("无权操作该优惠券");
        }
        voucher.setStatus(status);
        updateById(voucher);
        stringRedisTemplate.delete(CACHE_VOUCHER_LIST_KEY + voucher.getShopId());
        return Result.ok();
    }
}
