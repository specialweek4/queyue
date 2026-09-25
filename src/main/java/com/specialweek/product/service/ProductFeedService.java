package com.specialweek.product.service;

import com.specialweek.product.api.dto.ProductFeedPageResponse;

public interface ProductFeedService {
    /**
     * 分页查询公共商品并覆盖实时收藏计数和用户状态。
     * @param page
     * @param size
     * @param currentUserId
     * @return
     */
    ProductFeedPageResponse publicFeed(int page, int size, Long currentUserId);

    /**
     * 读取版本化收藏页，仅在 Redis 命中或数据库回源时覆盖计数和用户状态。
     * @param userId
     * @param page
     * @param size
     * @return
     */
    ProductFeedPageResponse myFavorites(long userId, int page, int size);

    /**
     * 定向清除公共商品页面和商品基础片段缓存。
     * @param productId
     */
    void invalidateProduct(long productId);
}
