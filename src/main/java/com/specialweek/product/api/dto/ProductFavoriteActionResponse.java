package com.specialweek.product.api.dto;

public record ProductFavoriteActionResponse(
        long productId,
        boolean active,
        boolean changed,
        long count
) {}
