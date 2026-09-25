package com.specialweek.voucher.api;

import com.specialweek.common.web.Result;
import com.specialweek.voucher.api.dto.SeckillVoucherCreateRequest;
import com.specialweek.voucher.service.IVoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
public class MerchantSeckillVoucherController {

    private final IVoucherService voucherService;

    @PostMapping("/shops/{shopId}/seckill-vouchers")
    public Result createSeckill(@PathVariable long shopId,
                                @Valid @RequestBody SeckillVoucherCreateRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return voucherService.createSeckill(shopId, request, userId(jwt), role(jwt));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private int role(Jwt jwt) {
        Object role = jwt.getClaim("role");
        return role instanceof Number number ? number.intValue() : 0;
    }
}
