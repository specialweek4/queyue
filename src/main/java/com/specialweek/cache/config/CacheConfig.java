package com.specialweek.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.specialweek.blog.api.dto.FeedPageResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.specialweek.blog.api.dto.BlogDetailResponse;

import java.time.Duration;
import com.specialweek.product.api.dto.ProductFeedPageResponse;
import com.specialweek.product.api.dto.ProductDetailResponse;

@Configuration
public class CacheConfig {

    @Bean("feedPublicCache")
    public Cache<String, FeedPageResponse> feedPublicCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getPublicCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(props.getL2().getPublicCfg().getTtlSeconds()))
                .build();
    }

    @Bean("feedMineCache")
    public Cache<String, FeedPageResponse> feedMineCache(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getMineCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(props.getL2().getMineCfg().getTtlSeconds()))
                .build();
    }

    @Bean("blogDetailCache")
    public Cache<String, BlogDetailResponse> blogDetailCache(
            CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getBlogDetail().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(
                        props.getL2().getBlogDetail().getTtlSeconds()))
                .build();
    }
    /**
     * 创建商品公共页面本地缓存。
     * @param props
     * @return
     */
    @Bean("productPublicCache")
    public Cache<String, ProductFeedPageResponse> productPublicCache(
            CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getProductPublic().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(
                        props.getL2().getProductPublic().getTtlSeconds()))
                .build();
    }

    /**
     * 创建用户商品收藏页面本地缓存。
     * @param props
     * @return
     */
    @Bean("productMineCache")
    public Cache<String, ProductFeedPageResponse> productMineCache(
            CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getProductMine().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(
                        props.getL2().getProductMine().getTtlSeconds()))
                .build();
    }

    /**
     * 创建商品详情本地缓存。
     * @param props
     * @return
     */
    @Bean("productDetailCache")
    public Cache<String, ProductDetailResponse> productDetailCache(
            CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getL2().getProductDetail().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(
                        props.getL2().getProductDetail().getTtlSeconds()))
                .build();
    }
}
