package com.specialweek.voucher.service;

import com.specialweek.common.web.Result;
import com.specialweek.voucher.api.dto.SeckillVoucherCreateRequest;
import com.specialweek.voucher.api.dto.VoucherCreateRequest;
import com.specialweek.voucher.domain.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author specialweek
 * @since 2026-08-15
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    Result createNormal(long shopId, VoucherCreateRequest request, long userId, int role);

    Result createSeckill(long shopId, SeckillVoucherCreateRequest request, long userId, int role);

    Result listMine(long shopId, long userId, int role);

    Result changeStatus(long voucherId, int status, long userId, int role);
}
