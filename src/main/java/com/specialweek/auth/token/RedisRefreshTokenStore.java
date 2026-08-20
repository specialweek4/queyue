package com.specialweek.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void store(long userId, String tokenId, Duration ttl) {
        redisTemplate.opsForValue().set(key(userId, tokenId), "1", ttl);
    }

    @Override
    public boolean consume(long userId, String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.delete(key(userId, tokenId)));
    }

    @Override
    public void revoke(long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    @Override
    public void revokeAll(long userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String key(long userId, String tokenId) {
        return KEY_PREFIX + userId + ":" + tokenId;
    }
}
