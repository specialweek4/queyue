package com.specialweek.shop.api;

import com.specialweek.common.web.Result;
import com.specialweek.shop.api.dto.ShopAuditRequest;
import com.specialweek.shop.service.ShopApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/shop-applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShopApplicationController {

    private final ShopApplicationService service;

    @GetMapping
    public Result list(@RequestParam(value = "applyType", required = false) Integer applyType,
                       @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
                       @RequestParam(value = "current", defaultValue = "1") int current,
                       @RequestParam(value = "size", defaultValue = "10") int size) {
        return service.listForAdmin(applyType, auditStatus, current, size);
    }

    @PutMapping("/{id}/audit")
    public Result audit(@PathVariable long id,
                        @Valid @RequestBody ShopAuditRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        return service.audit(id, request, Long.parseLong(jwt.getSubject()));
    }
}
