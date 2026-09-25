package com.specialweek.shop.api;

import com.specialweek.common.web.Result;
import com.specialweek.shop.api.dto.ShopApplyRequest;
import com.specialweek.shop.service.ShopApplicationService;
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
@RequestMapping("/merchant/shop-applications")
@RequiredArgsConstructor
public class MerchantShopApplicationController {

    private final ShopApplicationService service;

    @PostMapping
    public Result apply(@Valid @RequestBody ShopApplyRequest request,
                        @AuthenticationPrincipal Jwt jwt) {
        return service.apply(request, userId(jwt));
    }

    @GetMapping("/mine")
    public Result mine(@AuthenticationPrincipal Jwt jwt,
                       @RequestParam(value = "current", defaultValue = "1") int current,
                       @RequestParam(value = "size", defaultValue = "10") int size) {
        return service.mine(userId(jwt), current, size);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable long id,
                         @Valid @RequestBody ShopApplyRequest request,
                         @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, request, userId(jwt));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
