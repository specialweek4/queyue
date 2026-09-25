package com.specialweek.blog.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.specialweek.blog.api.dto.BlogDetailResponse;
import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.model.BlogDetailRow;
import com.specialweek.blog.service.BlogDetailService;
import com.specialweek.counter.dto.BlogFlags;
import com.specialweek.counter.service.BitmapStateReader;
import com.specialweek.counter.service.CounterService;
import com.specialweek.follow.service.FollowStateService;
import com.specialweek.storage.OssStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class BlogDetailServiceImpl implements BlogDetailService {
    private static final int LAYOUT_VERSION = 1;
    private static final String NULL_VALUE = "__null__";

    private final BlogMapper blogMapper;
    private final CounterService counterService;
    private final BitmapStateReader bitmapStateReader;
    private final FollowStateService followStateService;
    private final OssStorageService ossStorageService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, BlogDetailResponse> localCache;
    private final ConcurrentHashMap<String, Object> singleFlight =
            new ConcurrentHashMap<>();

    public BlogDetailServiceImpl(
            BlogMapper blogMapper,
            CounterService counterService,
            BitmapStateReader bitmapStateReader,
            FollowStateService followStateService,
            OssStorageService ossStorageService,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Qualifier("blogDetailCache")
            Cache<String, BlogDetailResponse> localCache) {
        this.blogMapper = blogMapper;
        this.counterService = counterService;
        this.bitmapStateReader = bitmapStateReader;
        this.followStateService = followStateService;
        this.ossStorageService = ossStorageService;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.localCache = localCache;
    }

    private static String detailKey(long blogId) {
        return "blog:detail:" + blogId + ":v" + LAYOUT_VERSION;
    }

    /**
     * 获取blog详情
     * @param blogId
     * @param uid
     * @return
     */
    @Override
    public BlogDetailResponse detail(long blogId, Long uid) {
        String key = detailKey(blogId);

        BlogDetailResponse local = localCache.getIfPresent(key);
        if (local != null) {
            return enrich(local, uid);
        }

        String cached = redis.opsForValue().get(key);
        //检查是否为空页
        if (NULL_VALUE.equals(cached)) {
            return null;
        }
        //反序列化
        BlogDetailResponse redisHit = deserialize(cached, key);
        if (redisHit != null) {
            localCache.put(key, redisHit);
            return enrich(redisHit, uid);
        }


        Object lock = singleFlight.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                //重查缓存，防止重复回源
                BlogDetailResponse doubleLocal = localCache.getIfPresent(key);
                if (doubleLocal != null) {
                    return enrich(doubleLocal, uid);
                }

                String again = redis.opsForValue().get(key);
                if (NULL_VALUE.equals(again)) {
                    return null;
                }
                BlogDetailResponse doubleRedis = deserialize(again, key);
                if (doubleRedis != null) {
                    localCache.put(key, doubleRedis);
                    return enrich(doubleRedis, uid);
                }

                //触发数据库回源
                BlogDetailRow row = blogMapper.selectDetailById(blogId);
                if (row == null) {
                    redis.opsForValue().set(key, NULL_VALUE,
                            Duration.ofSeconds(30
                                    + ThreadLocalRandom.current().nextInt(31)));
                    return null;
                }

                boolean published = Integer.valueOf(1).equals(row.getStatus());
                boolean owner = uid != null && uid.equals(row.getUserId());
                if (!published && !owner) {
                    return null;
                }

                BlogDetailResponse base = toBase(row);

                // 草稿和回收站只能作者查看，不进入共享缓存。
                if (!published) {
                    return enrich(base, uid);
                }

                try {
                    Duration ttl = Duration.ofSeconds(60
                            + ThreadLocalRandom.current().nextInt(30));
                    redis.opsForValue().set(key,
                            objectMapper.writeValueAsString(base), ttl);
                    localCache.put(key, base);
                } catch (Exception e) {
                    log.warn("Blog 详情缓存写入失败: key={}", key, e);
                }
                return enrich(base, uid);
            }
        } finally {
            singleFlight.remove(key, lock);
        }
    }

    /**
     * 将json数据反序列化为DTO
     * @param json
     * @param key
     * @return
     */
    private BlogDetailResponse deserialize(String json, String key) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, BlogDetailResponse.class);
        } catch (Exception e) {
            // 坏值删除后回源修复，不能把反序列化异常当成永久未命中。
            redis.delete(key);
            return null;
        }
    }

    /**
     * 将数据库查询的结果映射成DTO
     * @param row
     * @return
     */
    private BlogDetailResponse toBase(BlogDetailRow row) {
        //把 Blog 正文的 OSS 对象 Key 转换成前端可以访问的公开 URL：
        String contentUrl = StrUtil.isBlank(row.getContentObjectKey())
                ? null
                : ossStorageService.publicUrl(row.getContentObjectKey());
        return new BlogDetailResponse(
                row.getId(), row.getUserId(), row.getTitle(),
                row.getDescription(), row.getImages(), row.getCoverUrl(),row.getContentObjectKey(),
                contentUrl, row.getComments(), row.getStatus(),
                row.getPublishTime(), row.getCreateTime(), row.getUpdateTime(),
                row.getName(), row.getIcon(),
                0L, 0L, false, false, false);
    }

    private BlogDetailResponse enrich(BlogDetailResponse base, Long uid) {
        long liked = base.liked();
        long favorites = base.favorites();
        boolean isLike = false;
        boolean faved = false;
        boolean followed = false;

        if (Integer.valueOf(1).equals(base.status())) {
            Map<String, Long> counts = counterService.getCounts(
                    "blog", String.valueOf(base.id()), List.of("like", "fav"));
            liked = counts.getOrDefault("like", 0L);
            favorites = counts.getOrDefault("fav", 0L);

            //用户态
            if (uid != null) {
                BlogFlags flags = bitmapStateReader
                        .getFlagsBatch(List.of(base.id()), uid)
                        .get(base.id());
                isLike = flags != null && flags.liked();
                faved = flags != null && flags.favorited();
                followed = followStateService.isFollowed(uid, base.userId());
            }
        }

        return new BlogDetailResponse(
                base.id(), base.userId(), base.title(), base.description(),
                base.images(), base.coverUrl(), base.contentObjectKey(), base.contentUrl(),
                base.comments(), base.status(), base.publishTime(),
                base.createTime(), base.updateTime(), base.name(), base.icon(),
                liked, favorites, isLike, faved, followed);
    }

    @Override
    public void invalidate(long blogId) {
        String key = detailKey(blogId);

        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Blog 详情 Redis 缓存删除失败: key={}", key, e);
        }

        try {
            localCache.invalidate(key);
        } catch (Exception e) {
            log.warn("Blog 详情本地缓存删除失败: key={}", key, e);
        }
    }
}
