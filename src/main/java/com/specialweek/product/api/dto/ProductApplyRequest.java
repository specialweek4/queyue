package com.specialweek.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductApplyRequest(
        @NotBlank
        @Size(max = 128)
        String name,

        @Size(max = 1024)
        String description,

        @Size(max = 6)
        List<@NotBlank @Size(max = 512) String> imageKeys,

        @NotNull
        @PositiveOrZero
        Long price,

        @NotNull
        @PositiveOrZero
        Integer stock
) {
}
