package com.specialweek.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.specialweek.counter.service.CounterService;
import com.specialweek.product.api.dto.ProductDetailResponse;
import com.specialweek.product.mapper.ProductMapper;
import com.specialweek.product.model.ProductDetailRow;
import com.specialweek.product.service.ProductDetailService;
import com.specialweek.product.service.ProductFavoriteStateReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ProductDetailServiceImpl implements ProductDetailService {
    private static final int LAYOUT_VERSION = 1;
    private static final String NULL_VALUE = "__null__";

    private final ProductMapper productMapper;
    private final CounterService counterService;
    private final ProductFavoriteStateReader stateReader;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, ProductDetailResponse> localCache;
    private final Object[] locks = java.util.stream.IntStream.range(0, 256)
            .mapToObj(i -> new Object()).toArray();

    /**
     * 注入商品详情查询、收藏状态和缓存依赖。
     * @param productMapper
     * @param counterService
     * @param stateReader
     * @param redis
     * @param objectMapper
     * @param localCache
     */
    public ProductDetailServiceImpl(
            ProductMapper productMapper,
            CounterService counterService,
            ProductFavoriteStateReader stateReader,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Qualifier("productDetailCache")
            Cache<String, ProductDetailResponse> localCache) {
        this.productMapper = productMapper;
        this.counterService = counterService;
        this.stateReader = stateReader;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.localCache = localCache;
    }

    /**
     * 读取商品和商铺基础详情并补充实时收藏信息。
     * @param productId
     * @param uid
     * @return
     */
    @Override
    public ProductDetailResponse detail(long productId, Long uid) {
        String key = detailKey(productId);

        ProductDetailResponse local = localCache.getIfPresent(key);
        if (local != null) {
            return enrich(local, uid);
        }

        String cached = redis.opsForValue().get(key);
        if (NULL_VALUE.equals(cached)) {
            return null;
        }
        ProductDetailResponse redisHit = deserialize(cached, key);
        if (redisHit != null) {
            localCache.put(key, redisHit);
            return enrich(redisHit, uid);
        }

        Object lock = locks[Math.floorMod(key.hashCode(), locks.length)];
            synchronized (lock) {
                ProductDetailResponse doubleLocal = localCache.getIfPresent(key);
                if (doubleLocal != null) {
                    return enrich(doubleLocal, uid);
                }

                String again = redis.opsForValue().get(key);
                if (NULL_VALUE.equals(again)) {
                    return null;
                }
                ProductDetailResponse doubleRedis = deserialize(again, key);
                if (doubleRedis != null) {
                    localCache.put(key, doubleRedis);
                    return enrich(doubleRedis, uid);
                }

                ProductDetailRow row = productMapper.selectPublicDetail(productId);
                if (row == null) {
                    redis.opsForValue().set(key, NULL_VALUE,
                            Duration.ofSeconds(30
                                    + ThreadLocalRandom.current().nextInt(31)));
                    return null;
                }

                ProductDetailResponse base = toBase(row);
                try {
                    Duration ttl = Duration.ofSeconds(60
                            + ThreadLocalRandom.current().nextInt(30));
                    redis.opsForValue().set(key,
                            objectMapper.writeValueAsString(base), ttl);
                    localCache.put(key, base);
                } catch (Exception e) {
                    log.warn("商品详情缓存写入失败: key={}", key, e);
                }
                return enrich(base, uid);
            }
    }

    /**
     * 读取缓存详情并删除无法解析的缓存值。
     * @param json
     * @param key
     * @return
     */
    private ProductDetailResponse deserialize(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductDetailResponse.class);
        } catch (Exception e) {
            redis.delete(key);
            return null;
        }
    }

    /**
     * 构建只包含商品和商铺信息的基础详情。
     * @param row
     * @return
     */
    private ProductDetailResponse toBase(ProductDetailRow row) {
        return new ProductDetailResponse(
                row.getId(), row.getShopId(),
                row.getName(), row.getDescription(), row.getImages(),
                row.getPrice(), row.getStock(), row.getStatus(),
                row.getCreateTime(), row.getUpdateTime(),
                row.getShopName(),
                row.getShopImages(), row.getShopAddress(), row.getShopOpenHours(),
                0L, false);
    }

    /**
     * 覆盖详情中的实时收藏计数和用户状态。
     * @param base
     * @param uid
     * @return
     */
    private ProductDetailResponse enrich(ProductDetailResponse base, Long uid) {
        long favorites = counterService.getCounts(
                        "product", String.valueOf(base.id()), List.of("fav"))
                .getOrDefault("fav", 0L);
        boolean faved = uid != null && Boolean.TRUE.equals(
                stateReader.getBatch(List.of(base.id()), uid).get(base.id()));

        return new ProductDetailResponse(
                base.id(), base.shopId(), base.name(),
                base.description(), base.images(), base.price(), base.stock(),
                base.status(), base.createTime(), base.updateTime(),
                base.shopName(),
                base.shopImages(), base.shopAddress(), base.shopOpenHours(),
                favorites, faved);
    }

    /**
     * 递增该商品的详情版本并清除原版本的 Redis 和本地缓存。
     * @param productId
     */
    @Override
    public void invalidate(long productId) {
        String key = detailKey(productId);
        redis.opsForValue().increment(versionKey(productId));
        redis.delete(key);
        localCache.invalidate(key);
    }

    /**
     * 读取商品变更版本并生成详情缓存键。
     * @param productId
     * @return
     */
    private String detailKey(long productId) {
        String version = redis.opsForValue().get(versionKey(productId));
        return "product:detail:" + productId + ":v" + LAYOUT_VERSION
                + ":version:" + (version == null ? "0" : version);
    }

    /**
     * 生成仅用于指定商品详情的变更版本键。
     * @param productId
     * @return
     */
    private static String versionKey(long productId) {
        return "product:detail:version:" + productId;
    }
}
