package com.specialweek.blog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.specialweek.blog.api.dto.FeedItemResponse;
import com.specialweek.blog.api.dto.FeedPageResponse;
import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.model.BlogFeedRow;
import com.specialweek.blog.service.BlogFeedService;
import com.specialweek.counter.dto.BlogFlags;
import com.specialweek.counter.service.BitmapStateReader;
import com.specialweek.counter.service.CounterService;
import com.specialweek.follow.service.FollowStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class BlogFeedServiceImpl implements BlogFeedService {

    private static final Logger log = LoggerFactory.getLogger(BlogFeedServiceImpl.class);
    private static final int LAYOUT_VER = 1;
    private static final int CACHED_PAGES = 3;
    private static final String ITEM_PREFIX = "feed:item:";
    private static final String IDS_PREFIX = "feed:hot:ids:";
    private static final String EMPTY_MARK = "__empty__";
    private static final int IDS_BASE_TTL = 20;
    private static final int IDS_TTL_JITTER = 5;
    private static final Duration ITEM_TTL = Duration.ofMinutes(10);
    private static final Duration HAS_MORE_TTL = Duration.ofSeconds(10);

    private final BlogMapper blogMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CounterService counterService;
    private final BitmapStateReader bitmapStateReader;
    private final FollowStateService followStateService;
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();

    public BlogFeedServiceImpl(
            BlogMapper blogMapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CounterService counterService,
            BitmapStateReader bitmapStateReader,
            FollowStateService followStateService,
            @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache) {
        this.blogMapper = blogMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.counterService = counterService;
        this.bitmapStateReader = bitmapStateReader;
        this.followStateService = followStateService;
        this.feedPublicCache = feedPublicCache;
    }

    private String cacheKey(int page, int size) {
        return "feed:hot:" + size + ":" + page + ":v" + LAYOUT_VER;
    }

    @Override
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        String localPageKey = cacheKey(safePage, safeSize);

        if (safePage > CACHED_PAGES) {
            int offset = (safePage - 1) * safeSize;
            List<BlogFeedRow> rows = blogMapper.selectHotFeed(safeSize, offset);
            return new FeedPageResponse(enrich(mapRowsToItems(rows), currentUserIdNullable),
                    safePage, safeSize, false);
        }

        String idsKey = IDS_PREFIX + safePage;
        String hasMoreKey = idsKey + ":hasMore";

        FeedPageResponse local = feedPublicCache.getIfPresent(localPageKey);
        if (local != null && local.items() != null) {
            log.info("feed.hot source=local localPageKey={} page={} size={}", localPageKey, safePage, safeSize);
            return new FeedPageResponse(enrich(local.items(), currentUserIdNullable),
                    local.page(), local.size(), local.hasMore());
        }

        FeedPageResponse fromCache = assembleFromCache(idsKey, hasMoreKey, safePage, safeSize);
        if (fromCache != null) {
            feedPublicCache.put(localPageKey, fromCache);
            log.info("feed.hot source=3tier localPageKey={} page={} size={}", localPageKey, safePage, safeSize);
            return new FeedPageResponse(enrich(fromCache.items(), currentUserIdNullable),
                    fromCache.page(), fromCache.size(), fromCache.hasMore());
        }

        Object lock = singleFlight.computeIfAbsent(idsKey, k -> new Object());
        synchronized (lock) {
            FeedPageResponse again = assembleFromCache(idsKey, hasMoreKey, safePage, safeSize);
            if (again != null) {
                feedPublicCache.put(localPageKey, again);
                singleFlight.remove(idsKey);
                log.info("feed.hot source=3tier(after-flight) localPageKey={} page={} size={}",
                        localPageKey, safePage, safeSize);
                return new FeedPageResponse(enrich(again.items(), currentUserIdNullable),
                        again.page(), again.size(), again.hasMore());
            }

            int offset = (safePage - 1) * safeSize;
            List<BlogFeedRow> rows = blogMapper.selectHotFeed(safeSize + 1, offset);
            boolean hasMore = rows.size() > safeSize;
            if (hasMore) {
                rows = rows.subList(0, safeSize);
            }

            List<FeedItemResponse> items = mapRowsToItems(rows);
            FeedPageResponse respForCache = new FeedPageResponse(items, safePage, safeSize, hasMore);
            try {
                writeCaches(idsKey, hasMoreKey, items, hasMore);
            } catch (Exception e) {
                log.warn("feed.hot 缓存回填失败: page={} size={}", safePage, safeSize, e);
            }
            feedPublicCache.put(localPageKey, respForCache);
            singleFlight.remove(idsKey);

            log.info("feed.hot source=db localPageKey={} page={} size={} hasMore={}",
                    localPageKey, safePage, safeSize, hasMore);
            return new FeedPageResponse(enrich(items, currentUserIdNullable),
                    safePage, safeSize, hasMore);
        }
    }

    private FeedPageResponse assembleFromCache(String idsKey, String hasMoreKey, int page, int size) {
        List<String> idList = redis.opsForList().range(idsKey, 0, size - 1);
        if (idList == null || idList.isEmpty()) {
            return null;
        }
        if (idList.size() == 1 && EMPTY_MARK.equals(idList.get(0))) {
            return new FeedPageResponse(List.of(), page, size, false);
        }

        List<String> itemKeys = new ArrayList<>(idList.size());
        for (String id : idList) {
            itemKeys.add(ITEM_PREFIX + id);
        }
        List<String> itemJsons = redis.opsForValue().multiGet(itemKeys);

        List<FeedItemResponse> items = new ArrayList<>(idList.size());
        for (int i = 0; i < idList.size(); i++) {
            String itemJson = (itemJsons != null && i < itemJsons.size()) ? itemJsons.get(i) : null;
            if (itemJson == null) {
                return null;
            }
            try {
                items.add(objectMapper.readValue(itemJson, FeedItemResponse.class));
            } catch (Exception e) {
                return null;
            }
        }

        String hasMoreStr = redis.opsForValue().get(hasMoreKey);
        boolean hasMore = hasMoreStr != null ? "1".equals(hasMoreStr) : (idList.size() == size);
        return new FeedPageResponse(items, page, size, hasMore);
    }

    private List<FeedItemResponse> enrich(List<FeedItemResponse> base, Long uid) {
        if (base.isEmpty()) {
            return List.of();
        }
        List<Long> blogIds = base.stream().map(FeedItemResponse::id).distinct().toList();
        Map<String, Map<String, Long>> rawCounts = readCounts(blogIds);
        Map<Long, BlogFlags> flags = uid == null ? new HashMap<>() : readFlags(blogIds, uid);
        List<Long> authorIds = base.stream().map(FeedItemResponse::userId).distinct().toList();
        Map<Long, Boolean> followed = uid == null ? new HashMap<>() : readFollowed(uid, authorIds);

        List<FeedItemResponse> out = new ArrayList<>(base.size());
        for (FeedItemResponse it : base) {
            Map<String, Long> c = rawCounts.getOrDefault(String.valueOf(it.id()), Map.of());
            BlogFlags f = flags.get(it.id());
            Boolean flw = followed.get(it.userId());
            out.add(new FeedItemResponse(
                    it.id(), it.userId(), it.title(), it.description(), it.images(), it.coverUrl(),
                    c.getOrDefault("like", 0L),
                    c.getOrDefault("fav", 0L),
                    it.comments(), it.publishTime(), it.name(), it.icon(),
                    f != null && f.liked(), f != null && f.favorited(), Boolean.TRUE.equals(flw)));
        }
        return out;
    }

    private Map<String, Map<String, Long>> readCounts(List<Long> blogIds) {
        try {
            return counterService.getCountsBatch("blog",
                    blogIds.stream().map(String::valueOf).toList(), List.of("like", "fav"));
        } catch (RuntimeException e) {
            log.warn("计数服务不可用，降级使用 MySQL 检查点计数: blogs={}", blogIds, e);
            Map<String, Map<String, Long>> fallback = new LinkedHashMap<>();
            blogMapper.selectBatchIds(blogIds).forEach(blog -> {
                Map<String, Long> m = new HashMap<>();
                m.put("like", blog.getLiked() == null ? 0L : blog.getLiked());
                m.put("fav", blog.getFavorites() == null ? 0L : blog.getFavorites());
                fallback.put(String.valueOf(blog.getId()), m);
            });
            return fallback;
        }
    }

    private Map<Long, BlogFlags> readFlags(List<Long> blogIds, long uid) {
        try {
            return bitmapStateReader.getFlagsBatch(blogIds, uid);
        } catch (RuntimeException e) {
            log.warn("用户态读取失败，临时按 false 处理: user={}", uid, e);
            return blogIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> new BlogFlags(false, false)));
        }
    }

    private Map<Long, Boolean> readFollowed(long uid, List<Long> authorIds) {
        try {
            return followStateService.getBatch(uid, authorIds);
        } catch (RuntimeException e) {
            log.warn("关注状态读取失败，临时按 false 处理: user={}", uid, e);
            return authorIds.stream().collect(Collectors.toMap(id -> id, id -> false));
        }
    }

    private void writeCaches(String idsKey, String hasMoreKey, List<FeedItemResponse> items, boolean hasMore) {
        if (items.isEmpty()) {
            redis.opsForList().leftPushAll(idsKey, EMPTY_MARK);
            redis.expire(idsKey, HAS_MORE_TTL);
            redis.opsForValue().set(hasMoreKey, "0", HAS_MORE_TTL);
            return;
        }

        List<String> idVals = new ArrayList<>(items.size());
        for (FeedItemResponse it : items) {
            idVals.add(String.valueOf(it.id()));
        }
        redis.opsForList().leftPushAll(idsKey, idVals);
        redis.expire(idsKey, Duration.ofSeconds(IDS_BASE_TTL + ThreadLocalRandom.current().nextInt(IDS_TTL_JITTER + 1)));
        redis.opsForValue().set(hasMoreKey, hasMore ? "1" : "0", HAS_MORE_TTL);

        for (FeedItemResponse it : items) {
            try {
                String itemJson = objectMapper.writeValueAsString(it);
                redis.opsForValue().set(ITEM_PREFIX + it.id(), itemJson, ITEM_TTL);
            } catch (Exception ignored) {
            }
        }
    }

    private List<FeedItemResponse> mapRowsToItems(List<BlogFeedRow> rows) {
        List<FeedItemResponse> items = new ArrayList<>(rows.size());
        for (BlogFeedRow r : rows) {
            items.add(new FeedItemResponse(
                    r.getId(), r.getUserId(), r.getTitle(), r.getDescription(),
                    r.getImages(), r.getCoverUrl(),
                    0L, 0L,
                    r.getComments(), r.getPublishTime(), r.getName(), r.getIcon(),
                    false, false, false));
        }
        return items;
    }

    @Override
    public void invalidateFeedCache(long blogId) {
        feedPublicCache.invalidateAll();
        redis.delete(ITEM_PREFIX + blogId);
    }

    @Override
    public void invalidateFeedRanking() {
        feedPublicCache.invalidateAll();
        List<String> keys = new ArrayList<>();
        for (int page = 1; page <= CACHED_PAGES; page++) {
            String idsKey = IDS_PREFIX + page;
            keys.add(idsKey);
            keys.add(idsKey + ":hasMore");
        }
        redis.delete(keys);
    }
}
