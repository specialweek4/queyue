package com.specialweek.blog.api.dto;

import java.time.LocalDateTime;

public record FeedItemResponse(
        Long id,
        Long userId,
        String title,
        String description,
        String images,
        String coverUrl,
        long liked,
        long favorites,
        Integer comments,
        LocalDateTime publishTime,
        String name,
        String icon,
        boolean isLike,
        boolean faved,
        boolean followed) {
}
