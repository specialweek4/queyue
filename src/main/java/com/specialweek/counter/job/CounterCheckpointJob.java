package com.specialweek.counter.job;

import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.service.BlogFeedService;
import com.specialweek.counter.service.CounterService;
import com.specialweek.counter.util.RedisScanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CounterCheckpointJob {

    private static final String DIRTY_PATTERN = "dirty:v1:*";
    private static final String ENTITY_BLOG = "blog";

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> dirtyConfirmScript;
    private final CounterService counterService;
    private final BlogMapper blogMapper;
    private final BlogFeedService blogFeedService;

    @Value("${queyue.counter.checkpoint-batch:200}")
    private int batchSize;

    public CounterCheckpointJob(
            StringRedisTemplate redis,
            @Qualifier("counterDirtyConfirmScript") DefaultRedisScript<Long> counterDirtyConfirmScript,
            CounterService counterService,
            BlogMapper blogMapper,
            BlogFeedService blogFeedService) {
        this.redis = redis;
        this.dirtyConfirmScript = counterDirtyConfirmScript;
        this.counterService = counterService;
        this.blogMapper = blogMapper;
        this.blogFeedService = blogFeedService;
    }

    @Scheduled(fixedDelayString = "${queyue.counter.checkpoint-ms:30000}",
            initialDelayString = "${queyue.counter.checkpoint-initial-ms:15000}")
    public void checkpoint() {
        int batch = Math.max(1, batchSize);
        List<String> dirtyKeys = RedisScanUtil.scanKeysOrEmpty(redis, DIRTY_PATTERN, batch);
        boolean rankingChanged = false;
        for (String dirtyKey : dirtyKeys) {
            String[] parts = dirtyKey.split(":", 4);
            if (parts.length < 4 || !ENTITY_BLOG.equals(parts[2])) {
                log.warn("无法解析脏版本 key，跳过: {}", dirtyKey);
                continue;
            }
            long blogId;
            try {
                blogId = Long.parseLong(parts[3]);
            } catch (NumberFormatException e) {
                log.warn("脏版本 key 的实体 ID 非数字，跳过: {}", dirtyKey);
                continue;
            }
            if (syncOne(blogId, parts[2], parts[3], dirtyKey)) {
                rankingChanged = true;
            }
        }
        if (rankingChanged) {
            blogFeedService.invalidateFeedRanking();
        }
    }

    private boolean syncOne(long blogId, String entityType, String entityId, String dirtyKey) {
        String version = redis.opsForValue().get(dirtyKey);
        if (version == null) {
            return false;
        }
        Map<String, Long> counts;
        try {
            counts = counterService.getCounts(entityType, entityId, List.of("like", "fav"));
        } catch (RuntimeException e) {
            log.error("检查点读取计数失败，保留脏键: blog={}", blogId, e);
            return false;
        }
        try {
            blogMapper.updateCounterCheckpoint(
                    blogId,
                    clampInt(counts.getOrDefault("like", 0L)),
                    clampInt(counts.getOrDefault("fav", 0L)));
        } catch (RuntimeException e) {
            log.error("检查点写库失败，保留脏键: blog={}", blogId, e);
            return false;
        }
        redis.execute(dirtyConfirmScript, List.of(dirtyKey), version);
        return true;
    }

    private static int clampInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}
