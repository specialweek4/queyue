package com.specialweek.follow.service;

import com.specialweek.follow.mapper.FollowMapper;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class FollowStateService {

    private static final String KEY_PREFIX = "qy:state:follow:";
    private static final Duration BASE_TTL = Duration.ofMinutes(10);
    private static final int TTL_JITTER_SECONDS = 120;

    private final StringRedisTemplate redis;
    private final FollowMapper followMapper;

    public FollowStateService(StringRedisTemplate redis, FollowMapper followMapper) {
        this.redis = redis;
        this.followMapper = followMapper;
    }

    public boolean isFollowed(long userId, long targetUserId) {
        if (userId == targetUserId) {
            return false;
        }
        String key = stateKey(userId, targetUserId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return "1".equals(cached);
        }
        boolean followed = Boolean.TRUE.equals(followMapper.isFollowing(userId, targetUserId));
        redis.opsForValue().set(key, followed ? "1" : "0", randomTtl());
        return followed;
    }

    public Map<Long, Boolean> getBatch(long userId, List<Long> targetUserIds) {
        Map<Long, Boolean> result = new LinkedHashMap<>();
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return result;
        }
        List<Long> distinct = targetUserIds.stream().distinct().toList();
        List<String> keys = distinct.stream()
                .map(id -> stateKey(userId, id))
                .toList();
        List<Object> values = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < distinct.size(); i++) {
            long targetId = distinct.get(i);
            if (userId == targetId) {
                result.put(targetId, false);
                continue;
            }
            Object v = values != null && i < values.size() ? values.get(i) : null;
            if (v != null) {
                result.put(targetId, "1".equals(String.valueOf(v)));
            } else {
                missing.add(targetId);
            }
        }
        if (!missing.isEmpty()) {
            Set<Long> followedSet = new HashSet<>(followMapper.selectFollowedUserIds(userId, missing));
            for (long targetId : missing) {
                boolean followed = followedSet.contains(targetId);
                result.put(targetId, followed);
                redis.opsForValue().set(stateKey(userId, targetId), followed ? "1" : "0", randomTtl());
            }
        }
        return result;
    }

    public void setFollowState(long userId, long targetUserId, boolean following) {
        redis.opsForValue().set(stateKey(userId, targetUserId), following ? "1" : "0", randomTtl());
    }

    private static String stateKey(long userId, long targetUserId) {
        return KEY_PREFIX + userId + ":" + targetUserId;
    }

    private static Duration randomTtl() {
        return BASE_TTL.plusSeconds(ThreadLocalRandom.current().nextInt(TTL_JITTER_SECONDS + 1));
    }
}
