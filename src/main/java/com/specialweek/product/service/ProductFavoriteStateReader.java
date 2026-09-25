package com.specialweek.product.service;

import com.specialweek.counter.schema.BitmapShard;
import com.specialweek.counter.schema.CounterKeys;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductFavoriteStateReader {
    private final StringRedisTemplate redis;

    /**
     * 注入商品收藏位图读取依赖。
     * @param redis
     */
    public ProductFavoriteStateReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 通过 Redis 管道批量读取用户的商品收藏位图。
     * @param productIds
     * @param userId
     * @return
     */
    public Map<Long, Boolean> getBatch(List<Long> productIds, long userId) {
        Map<Long, Boolean> result = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;

        List<Long> ids = productIds.stream().distinct().toList();
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);

        List<Object> values = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : ids) {
                String key = CounterKeys.bitmapKey(
                        "fav", "product", String.valueOf(id), chunk);
                connection.stringCommands().getBit(
                        key.getBytes(StandardCharsets.UTF_8), bit);
            }
            return null;
        });

        for (int i = 0; i < ids.size(); i++) {
            Object raw = values != null && i < values.size() ? values.get(i) : null;
            boolean active = raw instanceof Boolean b
                    ? b
                    : raw instanceof Number n && n.longValue() != 0L;
            result.put(ids.get(i), active);
        }
        return result;
    }
}
