package com.specialweek.shop.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ShopApplyRequest(
        @NotBlank
        @Size(max = 128)
        String name,

        @NotNull
        Long typeId,

        @Size(max = 6)
        List<@NotBlank @Size(max = 512) String> imageKeys,

        @Size(max = 128)
        String area,

        @NotBlank
        @Size(max = 255)
        String address,

        @PositiveOrZero
        Long avgPrice,

        @Size(max = 32)
        String openHours
) {
}
