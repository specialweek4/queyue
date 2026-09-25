package com.specialweek.voucher.api;

import com.specialweek.common.web.Result;
import com.specialweek.voucher.api.dto.VoucherCreateRequest;
import com.specialweek.voucher.api.dto.VoucherStatusRequest;
import com.specialweek.voucher.service.IVoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MERCHANT','ADMIN')")
public class MerchantVoucherController {

    private final IVoucherService voucherService;

    @PostMapping("/shops/{shopId}/vouchers")
    public Result createNormal(@PathVariable long shopId,
                               @Valid @RequestBody VoucherCreateRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return voucherService.createNormal(shopId, request, userId(jwt), role(jwt));
    }

    @GetMapping("/shops/{shopId}/vouchers")
    public Result listMine(@PathVariable long shopId,
                           @AuthenticationPrincipal Jwt jwt) {
        return voucherService.listMine(shopId, userId(jwt), role(jwt));
    }

    @PutMapping("/vouchers/{id}/status")
    public Result changeStatus(@PathVariable long id,
                               @Valid @RequestBody VoucherStatusRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return voucherService.changeStatus(id, request.status(), userId(jwt), role(jwt));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private int role(Jwt jwt) {
        Object role = jwt.getClaim("role");
        return role instanceof Number number ? number.intValue() : 0;
    }
}
