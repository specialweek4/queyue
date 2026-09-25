package com.specialweek.blog.service;

import com.specialweek.blog.api.dto.FeedPageResponse;
import com.specialweek.common.web.Result;

public interface BlogFeedService {

    FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable);

    void invalidateCache(long blogId);

    Result Myfavs(long userId, int page, int size);

//    全量失效
//    void invalidateFeedRanking();
}
