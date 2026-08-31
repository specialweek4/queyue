package com.specialweek.blog.service;

import com.specialweek.blog.api.dto.FeedPageResponse;

public interface BlogFeedService {

    FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable);

    void invalidateFeedCache(long blogId);

    void invalidateFeedRanking();
}
