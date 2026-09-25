package com.specialweek.product.api.dto;

import java.time.LocalDateTime;

public record ProductFeedItemResponse(
        Long id,
        Long shopId,
        Long createdBy,
        String name,
        String description,
        String images,
        Long price,
        Integer stock,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        String creatorName,
        String creatorIcon,
        String shopName,
        long favorites,
        boolean faved
) {}
