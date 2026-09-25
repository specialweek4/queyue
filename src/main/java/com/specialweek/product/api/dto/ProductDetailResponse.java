package com.specialweek.product.api.dto;

import java.time.LocalDateTime;

public record ProductDetailResponse(
        Long id,
        Long shopId,
        String name,
        String description,
        String images,
        Long price,
        Integer stock,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        String shopName,
        String shopImages,
        String shopAddress,
        String shopOpenHours,
        long favorites,
        boolean faved
) {}
