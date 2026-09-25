package com.specialweek.blog.service;

import com.specialweek.blog.api.dto.BlogDetailResponse;

public interface BlogDetailService {
    BlogDetailResponse detail(long blogId, Long currentUserId);
    void invalidate(long blogId);
}
