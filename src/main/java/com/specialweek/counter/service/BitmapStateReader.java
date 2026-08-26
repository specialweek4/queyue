package com.specialweek.counter.service;

import com.specialweek.counter.dto.BlogFlags;
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
public class BitmapStateReader {

    private static final String ENTITY_TYPE = "blog";

    private final StringRedisTemplate redis;

    public BitmapStateReader(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Map<Long, BlogFlags> getFlagsBatch(List<Long> blogIds, long userId) {
        Map<Long, BlogFlags> result = new LinkedHashMap<>();
        if (blogIds == null || blogIds.isEmpty()) {
            return result;
        }
        List<Long> distinct = blogIds.stream().distinct().toList();
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);

        List<Object> values = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long blogId : distinct) {
                connection.stringCommands().getBit(
                        CounterKeys.bitmapKey("like", ENTITY_TYPE, String.valueOf(blogId), chunk)
                                .getBytes(StandardCharsets.UTF_8), bit);
                connection.stringCommands().getBit(
                        CounterKeys.bitmapKey("fav", ENTITY_TYPE, String.valueOf(blogId), chunk)
                                .getBytes(StandardCharsets.UTF_8), bit);
            }
            return null;
        });
        if (values == null) {
            return result;
        }
        for (int i = 0; i < distinct.size(); i++) {
            boolean liked = toBoolean(i * 2 < values.size() ? values.get(i * 2) : null);
            boolean favorited = toBoolean(i * 2 + 1 < values.size() ? values.get(i * 2 + 1) : null);
            result.put(distinct.get(i), new BlogFlags(liked, favorited));
        }
        return result;
    }

    private static boolean toBoolean(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.longValue() != 0L;
        }
        return "1".equals(o.toString()) || "true".equalsIgnoreCase(o.toString());
    }
}
