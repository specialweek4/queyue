package com.specialweek.product.service;

import com.specialweek.product.api.dto.ProductDetailResponse;

public interface ProductDetailService {
    /**
     * 读取商品和商铺基础详情并补充实时收藏信息。
     * @param productId
     * @param currentUserId
     * @return
     */
    ProductDetailResponse detail(long productId, Long currentUserId);
    /**
     * 递增该商品的详情版本并清除原版本的 Redis 和本地缓存。
     * @param productId
     */
    void invalidate(long productId);
}
