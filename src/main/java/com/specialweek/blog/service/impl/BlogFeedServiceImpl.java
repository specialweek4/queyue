package com.specialweek.blog.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.specialweek.blog.api.dto.FeedItemResponse;
import com.specialweek.blog.api.dto.FeedPageResponse;
import com.specialweek.blog.mapper.BlogMapper;
import com.specialweek.blog.model.BlogFeedRow;
import com.specialweek.blog.service.BlogFeedService;
import com.specialweek.common.web.Result;
import com.specialweek.counter.dto.BlogFlags;
import com.specialweek.counter.mapper.BlogBehaviorRelationMapper;
import com.specialweek.counter.service.BitmapStateReader;
import com.specialweek.counter.service.CounterService;
import com.specialweek.counter.util.RedisScanUtil;
import com.specialweek.follow.service.FollowStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BlogFeedServiceImpl implements BlogFeedService {

    private static final Logger log = LoggerFactory.getLogger(BlogFeedServiceImpl.class);
    private static final int LAYOUT_VER = 1;
    private static final String EMPTY_MARK = "__empty__";
    private static final String ITEM_PREFIX = "feed:item:";
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

    @Qualifier("feedPublicCache")
    private final Cache<String, FeedPageResponse> feedPublicCache;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();
    private final BlogBehaviorRelationMapper blogBehaviorRelationMapper;

    @Qualifier("feedMineCache")
    private final Cache<String, FeedPageResponse> feedMineCache;

    public BlogFeedServiceImpl(
            BlogMapper blogMapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            CounterService counterService,
            BitmapStateReader bitmapStateReader,
            FollowStateService followStateService,
            @Qualifier("feedPublicCache") Cache<String, FeedPageResponse> feedPublicCache, BlogBehaviorRelationMapper blogBehaviorRelationMapper, Cache<String, FeedPageResponse> feedMineCache) {
        this.blogMapper = blogMapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.counterService = counterService;
        this.bitmapStateReader = bitmapStateReader;
        this.followStateService = followStateService;
        this.feedPublicCache = feedPublicCache;
        this.blogBehaviorRelationMapper = blogBehaviorRelationMapper;
        this.feedMineCache = feedMineCache;
    }

    /**
     * 生成缓存键
     * @param page
     * @param size
     * @return
     */
    private String cacheKey(int page, int size) {
        return "feed:public:" + size + ":" + page + ":v" + LAYOUT_VER;
    }
    private String idsKey(int size, long hourSlot, int page){
        return "feed:public:ids:" + size + ":" + hourSlot + ":" + page;
    }
    private String hasMoreKey(int size, long hourSlot, int page) {
        return idsKey(size, hourSlot, page) + ":hasMore";
    }

    private String pagesRegistryKey(long hourSlot) {
        return "feed:public:pages:" + hourSlot;
    }

    private String reverseIndexKey(long blogId, long hourSlot) {
        return "feed:public:index:" + blogId + ":" + hourSlot;
    }


    @Override
    public FeedPageResponse getPublicFeed(int page, int size, Long currentUserIdNullable) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        long hourSlot = System.currentTimeMillis() / 3_600_000L;
        String localPageKey = cacheKey(safePage, safeSize);

        FeedPageResponse local = feedPublicCache.getIfPresent(localPageKey);
        if (local != null && local.items() != null) {
            log.info("feed.hot source=local localPageKey={} page={} size={}", localPageKey, safePage, safeSize);
            return new FeedPageResponse(enrich(local.items(), currentUserIdNullable),
                    local.page(), local.size(), local.hasMore());
        }

        String idsKey = idsKey(safeSize, hourSlot, safePage);
        String hasMoreKey = idsKey + ":hasMore";
        FeedPageResponse fromCache = assembleFromCache(idsKey, hasMoreKey, safePage, safeSize);
        if (fromCache != null) {
            feedPublicCache.put(localPageKey, fromCache);
            log.info("feed.hot source=3tier localPageKey={} page={} size={}", localPageKey, safePage, safeSize);
            return new FeedPageResponse(enrich(fromCache.items(), currentUserIdNullable),
                    fromCache.page(), fromCache.size(), fromCache.hasMore());
        }

        Object lock = singleFlight.computeIfAbsent(idsKey, k -> new Object());
        synchronized (lock) {
            //重查缓存防止重复回源。
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
                writeCaches(idsKey, localPageKey, hourSlot, hasMoreKey, items, hasMore, safeSize);
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

    /**
     * 重查缓存
     * @param idsKey
     * @param hasMoreKey
     * @param page
     * @param size
     * @return
     */
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

    /**
     * 填充计数和用户态
     * @param base
     * @param uid
     * @return
     */
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

    /**
     * 写入缓存
     * @param idsKey
     * @param loclPageKey -》存入登记表
     * @param hourSlot
     * @param hasMoreKey
     * @param items
     * @param hasMore
     */
    private void writeCaches(String idsKey, String loclPageKey, long hourSlot,
                             String hasMoreKey, List<FeedItemResponse> items, boolean hasMore,
                             int size) {

        Duration pageTTL = Duration.ofSeconds(60 + ThreadLocalRandom.current().nextInt(31));

        if (items.isEmpty()) {
            redis.opsForList().leftPushAll(idsKey, EMPTY_MARK);
            redis.expire(idsKey, pageTTL);
            redis.opsForValue().set(hasMoreKey, "0", Duration.ofSeconds(10));
        } else {
            List<String> idsValues = items.stream()
                    .map(it -> String.valueOf(it.id()))
                    .toList();

            redis.opsForList().rightPushAll(idsKey, idsValues);
            redis.expire(idsKey, pageTTL);
            if (idsValues.size() == size && hasMore) {
                redis.opsForValue().set(hasMoreKey, "1",
                        Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11)));
            } else {
                redis.opsForValue().set(hasMoreKey, hasMore ? "1" : "0", Duration.ofSeconds(10));
            }
        }

        //页面登记表
        String pageRegistryKey = pagesRegistryKey(hourSlot);
        redis.opsForSet().add(pageRegistryKey, loclPageKey);
        redis.expire(pageRegistryKey, pageTTL);

        for (FeedItemResponse item : items) {
            try {
                String itemKey = ITEM_PREFIX + item.id();
                String itemJson = objectMapper.writeValueAsString(item);
                redis.opsForValue().set(itemKey, itemJson, pageTTL);

                String indexKey = reverseIndexKey(item.id(), hourSlot);
                redis.opsForSet().add(indexKey, loclPageKey);
                redis.expire(indexKey, pageTTL);
            } catch (Exception ignore) {}
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

    /**
     * 删除blog相关联的缓存
     * @param id
     */
    @Override
    public void invalidateCache(long id){
        //TODO本地blog详情缓存
        //TODO redisBlog缓存
        try {
            invalidateFeedCache(id);
        } catch (Exception e) {
            log.warn("Feed 本地缓存清理失败，id={}，将依赖 TTL 自动过期", id, e);
        }
    }

    /**
     * 定向清理localpage缓存
     * @param blogId
     */
    private void invalidateFeedCache(long blogId) {
        long hourSolt = System.currentTimeMillis() / 3_600_000L;
        for(long solt : List.of(hourSolt, hourSolt - 1)){
            String index = reverseIndexKey(blogId, solt);
            try {
                Set<String> pageKeys = redis.opsForSet().members(index);
                if(pageKeys == null || pageKeys.isEmpty()){
                    continue;
                }
                for(String localpageKey : pageKeys){
                    if(localpageKey == null || localpageKey.isEmpty()){
                        continue;
                    }
                    feedPublicCache.invalidate(localpageKey);
                    redis.opsForSet().remove(index, localpageKey);
                }
            } catch (Exception e) {
                log.warn("\"Feed 缓存清理异常，indexKey={}\", indexKey, e");
            }
        }
    }

    /**
     * 生成“我的收藏”列表的缓存 Key（用户维度）。
     * @param userId 用户 ID
     * @param page 页码
     * @param size 每页大小
     * @return Redis 页面缓存 Key
     */
    private String myFavsCacheKey(long userId, int page, int size, long version) {
        return "feed:myfavs:" + userId + ":" + version + ":" + size + ":" + page;
    }

    @Override
    public Result Myfavs(long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 1);
        String key = myFavsCacheKey(userId, safePage, safeSize, getMyfavsVersion(userId));

        FeedPageResponse local = feedMineCache.getIfPresent(key);
        if(local != null){
            log.info("feed.myfavs source=local key={} page={} size={} user={}", key, safePage, safeSize, userId);
            return Result.ok(local);
        }

        String cached = redis.opsForValue().get(key);
        if(cached != null){
            try {
                FeedPageResponse fpr = objectMapper.readValue(cached, FeedPageResponse.class);
                List<FeedItemResponse> enriched = enrich(fpr.items(), userId);
                //命中缓存覆盖一下；
                FeedPageResponse res = new FeedPageResponse(enriched, fpr.page(), fpr.size(), fpr.hasMore());
                feedMineCache.put(key, res);
                log.info("feed.myfavs source=page key={} page={} size={} user={}", key, safePage, safeSize, userId);
                return Result.ok(res);
            } catch (Exception e) {}
        }

        int offset = (safePage - 1) * safeSize;
        List<Long> ids = blogBehaviorRelationMapper.selectFavoriteBlogIds(userId, offset, safeSize + 1);
        boolean hasMore = ids.size() > safeSize;
        if(hasMore) ids = ids.subList(0, safeSize);

        if(ids.isEmpty()){
            //ids为空的话返回and缓存空页
            FeedPageResponse empty = new FeedPageResponse(List.of(), safePage, safeSize, false);

            try {
                String json = objectMapper.writeValueAsString(empty);

                // 空页使用较短 TTL，防止缓存穿透，同时避免空结果保留太久
                Duration ttl = Duration.ofSeconds(
                        10 + ThreadLocalRandom.current().nextInt(11)
                );

                redis.opsForValue().set(key, json, ttl);
                feedMineCache.put(key, empty);
            } catch (Exception ignore) {}
            return Result.ok(empty);
        }
        List<BlogFeedRow> rows = blogMapper.selectFeedByIds(ids);

        //重排数据库查询的blog顺序，保证是按收藏顺序返回。
        Map<Long, BlogFeedRow> mapRow = rows.stream().collect(Collectors.toMap(
                BlogFeedRow::getId,
                Function.identity()
        ));

        List<BlogFeedRow> orderedRows = ids.stream()
                .map(mapRow::get)
                .filter(Objects::nonNull)
                .toList();

        List<FeedItemResponse> items = mapRowsToItems(orderedRows);

        FeedPageResponse res = new FeedPageResponse(enrich(items, userId), safePage, safeSize, hasMore);
        try {
            String Json = objectMapper.writeValueAsString(res);
            int baseTtl = 30;
            int jitter = ThreadLocalRandom.current().nextInt(20);
            redis.opsForValue().set(key, Json, Duration.ofSeconds(baseTtl + jitter));
            feedMineCache.put(key, res);
            log.info("feed.myfavs source=db key={} page={} size={} user={} hasMore={}", key, safePage, safeSize, userId, hasMore);
        } catch (Exception e) {}

        return Result.ok(res);
    }

    /**
     * 获取我的收藏页面的版本好
     * @param userId
     * @return
     */
    private long getMyfavsVersion(long userId) {
        String value = redis.opsForValue()
                .get("feed:myfavs:version:" + userId);

        return value == null ? 0L : Long.parseLong(value);
    }

//    /**
//     * 全量失效
//     */
//    @Override
//    public void invalidateFeedRanking() {
//        feedPublicCache.invalidateAll();
//        List<String> keys = new ArrayList<>();
//        for (int page = 1; page <= CACHED_PAGES; page++) {
//            String idsKey =idsKey();
//            keys.add(idsKey);
//            keys.add(idsKey + ":hasMore");
//        }
//        redis.delete(keys);
//    }
}
