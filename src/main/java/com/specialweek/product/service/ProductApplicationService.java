package com.specialweek.product.service;

import com.specialweek.common.web.Result;
import com.specialweek.product.api.dto.ProductApplyRequest;
import com.specialweek.product.api.dto.ProductAuditRequest;

public interface ProductApplicationService {

    Result apply(long shopId, ProductApplyRequest request, long userId, int role);

    Result mine(long shopId, long userId, int current, int size);

    Result update(long id, ProductApplyRequest request, long userId);

    Result listForAdmin(Integer auditStatus, int current, int size);

    Result audit(long id, ProductAuditRequest request, long adminId);

    Result listOfShop(long shopId, long userId, int role);

    Result changeStatus(long id, int status, long userId, int role);
}
