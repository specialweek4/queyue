package com.specialweek.shop.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShopAuditRequest(
        @NotNull Boolean approved,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal x,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal y,
        @Size(max = 512) String reason
) {
}
