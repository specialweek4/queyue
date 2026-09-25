package com.specialweek.shop.service;

import com.specialweek.common.web.Result;
import com.specialweek.shop.api.dto.ShopApplyRequest;
import com.specialweek.shop.api.dto.ShopAuditRequest;

public interface ShopApplicationService {

    Result apply(ShopApplyRequest request, long userId);

    Result applyUpdate(long shopId, ShopApplyRequest request, long userId, int role);

    Result mine(long userId, int current, int size);

    Result update(long id, ShopApplyRequest request, long userId);

    Result listForAdmin(Integer applyType, Integer auditStatus, int current, int size);

    Result audit(long id, ShopAuditRequest request, long adminId);
}
