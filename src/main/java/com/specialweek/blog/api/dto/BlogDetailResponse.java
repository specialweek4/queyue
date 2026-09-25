package com.specialweek.blog.api.dto;

import java.time.LocalDateTime;

public record BlogDetailResponse(
        Long id,
        Long userId,
        String title,
        String description,
        String images,
        String coverUrl,
        String contentObjectKey,
        String contentUrl,
        Integer comments,
        Integer status,
        LocalDateTime publishTime,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        String name,
        String icon,
        long liked,
        long favorites,
        boolean isLike,
        boolean faved,
        boolean followed
) {}
