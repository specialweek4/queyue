package com.specialweek.product.api;

import com.specialweek.common.web.Result;
import com.specialweek.product.api.dto.ProductApplyRequest;
import com.specialweek.product.api.dto.ProductStatusRequest;
import com.specialweek.product.service.ProductApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantProductApplicationController {

    private final ProductApplicationService service;

    @PostMapping("/shops/{shopId}/product-applications")
    public Result apply(@PathVariable long shopId,
                        @Valid @RequestBody ProductApplyRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        return service.apply(shopId, request, userId(jwt), role(jwt));
    }

    @GetMapping("/shops/{shopId}/product-applications/mine")
    public Result mine(@PathVariable long shopId,
                       @AuthenticationPrincipal Jwt jwt,
                       @RequestParam(value = "current", defaultValue = "1") int current,
                       @RequestParam(value = "size", defaultValue = "10") int size) {
        return service.mine(shopId, userId(jwt), current, size);
    }

    @PutMapping("/product-applications/{id}")
    public Result update(@PathVariable long id,
                         @Valid @RequestBody ProductApplyRequest request,
                         @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, userId(jwt));
    }

    @GetMapping("/shops/{shopId}/products")
    public Result listOfShop(@PathVariable long shopId,
                             @AuthenticationPrincipal Jwt jwt) {
        return service.listOfShop(shopId, userId(jwt), role(jwt));
    }

    @PutMapping("/products/{id}/status")
    public Result changeStatus(@PathVariable long id,
                               @Valid @RequestBody ProductStatusRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return service.changeStatus(id, request.status(), userId(jwt), role(jwt));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    private int role(Jwt jwt) {
        Object role = jwt.getClaim("role");
        return role instanceof Number number ? number.intValue() : 0;
    }
}
