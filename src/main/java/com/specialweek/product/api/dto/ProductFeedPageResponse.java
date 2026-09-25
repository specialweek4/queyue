package com.specialweek.product.api.dto;

import java.util.List;

public record ProductFeedPageResponse(
        List<ProductFeedItemResponse> items,
        int page,
        int size,
        boolean hasMore
) {}
