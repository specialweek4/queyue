package com.specialweek.voucher.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VoucherCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String subTitle,
        @Size(max = 1024) String rules,
        @NotNull @Positive Long payValue,
        @NotNull @Positive Long actualValue,
        Long productId
) {
}
