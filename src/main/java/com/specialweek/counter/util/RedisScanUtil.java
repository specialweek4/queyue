package com.specialweek.counter.util;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RedisScanUtil {

    public static List<String> scanKeys(StringRedisTemplate redis, String pattern, int batchSize) {
        return redis.execute((RedisCallback<List<String>>) connection -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(Math.max(10, batchSize))
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext() && keys.size() < batchSize) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    public static List<String> scanKeysOrEmpty(StringRedisTemplate redis, String pattern, int batchSize) {
        List<String> keys = scanKeys(redis, pattern, batchSize);
        return keys == null ? List.of() : keys;
    }

    public static List<String> scanAll(StringRedisTemplate redis, String pattern) {
        return redis.execute((RedisCallback<List<String>>) connection -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(200)
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    private RedisScanUtil() {
    }
}
