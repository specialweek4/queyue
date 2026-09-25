package com.specialweek.product.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.specialweek.counter.service.CounterService;
import com.specialweek.product.api.dto.ProductFeedItemResponse;
import com.specialweek.product.api.dto.ProductFeedPageResponse;
import com.specialweek.product.mapper.ProductFavoriteRelationMapper;
import com.specialweek.product.mapper.ProductMapper;
import com.specialweek.product.model.ProductFeedRow;
import com.specialweek.product.service.ProductFavoriteStateReader;
import com.specialweek.product.service.ProductFeedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductFeedServiceImpl implements ProductFeedService {
    private static final int LAYOUT_VERSION = 1;
    private static final String EMPTY = "__empty__";
    private static final String ITEM_PREFIX = "feed:product:item:";

    private final ProductMapper productMapper;
    private final ProductFavoriteRelationMapper favoriteMapper;
    private final CounterService counterService;
    private final ProductFavoriteStateReader stateReader;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Cache<String, ProductFeedPageResponse> publicCache;
    private final Cache<String, ProductFeedPageResponse> mineCache;
    private final ConcurrentHashMap<String, Object> singleFlight = new ConcurrentHashMap<>();

    /**
     * 注入商品 Feed 查询、收藏状态和缓存依赖。
     * @param productMapper
     * @param favoriteMapper
     * @param counterService
     * @param stateReader
     * @param redis
     * @param objectMapper
     * @param publicCache
     * @param mineCache
     */
    public ProductFeedServiceImpl(
            ProductMapper productMapper,
            ProductFavoriteRelationMapper favoriteMapper,
            CounterService counterService,
            ProductFavoriteStateReader stateReader,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Qualifier("productPublicCache")
            Cache<String, ProductFeedPageResponse> publicCache,
            @Qualifier("productMineCache")
            Cache<String, ProductFeedPageResponse> mineCache) {
        this.productMapper = productMapper;
        this.favoriteMapper = favoriteMapper;
        this.counterService = counterService;
        this.stateReader = stateReader;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.publicCache = publicCache;
        this.mineCache = mineCache;
    }

    /**
     * 生成商品公共页面的本地缓存键。
     * @param size
     * @param page
     * @return
     */
    private String localKey(int size, int page) {
        return "feed:product:" + size + ":" + page + ":v" + LAYOUT_VERSION;
    }

    /**
     * 生成指定小时槽的商品 ID 页面键。
     * @param size
     * @param slot
     * @param page
     * @return
     */
    private String idsKey(int size, long slot, int page) {
        return "feed:product:ids:" + size + ":" + slot + ":" + page;
    }

    /**
     * 生成页面后续数据标记键。
     * @param idsKey
     * @return
     */
    private String hasMoreKey(String idsKey) {
        return idsKey + ":hasMore";
    }

    /**
     * 生成小时槽的页面登记表键。
     * @param slot
     * @return
     */
    private String registryKey(long slot) {
        return "feed:product:pages:" + slot;
    }

    /**
     * 生成商品到公共页面的反向索引键。
     * @param productId
     * @param slot
     * @return
     */
    private String indexKey(long productId, long slot) {
        return "feed:product:index:" + productId + ":" + slot;
    }

    /**
     * 生成用户商品收藏版本键。
     * @param userId
     * @return
     */
    private String myVersionKey(long userId) {
        return "feed:product:myfavs:version:" + userId;
    }

    /**
     * 生成按用户收藏版本隔离的收藏页面键。
     * @param userId
     * @param version
     * @param size
     * @param page
     * @return
     */
    private String myPageKey(long userId, long version, int size, int page) {
        return "feed:product:myfavs:" + userId + ":" + version
                + ":" + size + ":" + page;
    }

    /**
     * 分页查询公共商品并覆盖实时收藏计数和用户状态。
     * @param page
     * @param size
     * @param uid
     * @return
     */
    @Override
    public ProductFeedPageResponse publicFeed(int page, int size, Long uid) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);

        long slot = System.currentTimeMillis() / 3_600_000L;
        String local = localKey(safeSize, safePage);
        ProductFeedPageResponse hit = publicCache.getIfPresent(local);
        if (hit != null) return enrichPage(hit, uid);

        String ids = idsKey(safeSize, slot, safePage);
        String more = hasMoreKey(ids);
        hit = assemble(ids, more, safePage, safeSize);
        if (hit != null) {
            publicCache.put(local, hit);
            return enrichPage(hit, uid);
        }

        Object lock = singleFlight.computeIfAbsent(ids, key -> new Object());
        synchronized (lock) {
            hit = assemble(ids, more, safePage, safeSize);
            if (hit != null) {
                publicCache.put(local, hit);
                singleFlight.remove(ids);
                return enrichPage(hit, uid);
            }

            ProductFeedPageResponse base = queryPublicDb(safePage, safeSize);
            try {
                writePublic(ids, more, slot, base);
            } catch (Exception e) {
                log.warn("商品 Feed 缓存回填失败: page={} size={}", safePage, safeSize, e);
            }
            publicCache.put(local, base);
            singleFlight.remove(ids);
            return enrichPage(base, uid);
        }
    }

    /**
     * 查询数据库中的公开商品并判断下一页。
     * @param page
     * @param size
     * @return
     */
    private ProductFeedPageResponse queryPublicDb(int page, int size) {
        int offset = Math.multiplyExact(page - 1, size);
        List<ProductFeedRow> rows = productMapper.selectPublicFeed(
                size + 1, offset);
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);
        return new ProductFeedPageResponse(
                mapRows(rows), page, size, hasMore);
    }

    /**
     * 通过 Redis 商品 ID 页面和基础片段还原页面。
     * @param idsKey
     * @param moreKey
     * @param page
     * @param size
     * @return
     */
    private ProductFeedPageResponse assemble(String idsKey, String moreKey,
                                             int page, int size) {
        List<String> ids = redis.opsForList().range(idsKey, 0, size - 1);
        if (ids == null || ids.isEmpty()) return null;
        if (ids.size() == 1 && EMPTY.equals(ids.get(0))) {
            return new ProductFeedPageResponse(List.of(), page, size, false);
        }

        List<String> itemKeys = ids.stream()
                .map(id -> ITEM_PREFIX + id).toList();
        List<String> jsonList = redis.opsForValue().multiGet(itemKeys);
        if (jsonList == null || jsonList.size() != ids.size()) return null;

        List<ProductFeedItemResponse> items = new ArrayList<>(ids.size());
        try {
            for (String json : jsonList) {
                if (json == null) return null;
                items.add(objectMapper.readValue(json,
                        ProductFeedItemResponse.class));
            }
        } catch (Exception e) {
            return null;
        }

        String more = redis.opsForValue().get(moreKey);
        boolean hasMore = more != null
                ? "1".equals(more)
                : ids.size() == size;
        return new ProductFeedPageResponse(items, page, size, hasMore);
    }

    /**
     * 按 Blog 的列表追加方式回填 Redis 页面并写入商品片段和反向索引。
     * @param idsKey
     * @param moreKey
     * @param slot
     * @param page
     */
    private void writePublic(String idsKey, String moreKey,
                             long slot, ProductFeedPageResponse page) {
        Duration pageTtl = Duration.ofSeconds(
                60 + ThreadLocalRandom.current().nextInt(31));
        Duration itemTtl = Duration.ofMinutes(10);
        if (page.items().isEmpty()) {
            redis.opsForList().leftPushAll(idsKey, EMPTY);
            redis.expire(idsKey, pageTtl);
            redis.opsForValue().set(moreKey, "0", Duration.ofSeconds(10));
        } else {
            List<String> ids = page.items().stream()
                    .map(item -> String.valueOf(item.id())).toList();
            redis.opsForList().rightPushAll(idsKey, ids);
            redis.expire(idsKey, pageTtl);
            Duration moreTtl = ids.size() == page.size() && page.hasMore()
                    ? Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11))
                    : Duration.ofSeconds(10);
            redis.opsForValue().set(moreKey, page.hasMore() ? "1" : "0", moreTtl);
        }

        String pageRef = page.size() + ":" + page.page();
        redis.opsForSet().add(registryKey(slot), pageRef);
        redis.expire(registryKey(slot), Duration.ofSeconds(150));

        for (ProductFeedItemResponse item : page.items()) {
            try {
                redis.opsForValue().set(ITEM_PREFIX + item.id(),
                        objectMapper.writeValueAsString(item), itemTtl);
                String index = indexKey(item.id(), slot);
                redis.opsForSet().add(index, pageRef);
                redis.expire(index, Duration.ofSeconds(150));
            } catch (Exception e) {
                log.warn("商品 Feed 缓存片段写入失败: product={}", item.id(), e);
            }
        }
    }

    /**
     * 读取版本化收藏页，仅在 Redis 命中或数据库回源时覆盖计数和用户状态。
     * @param uid
     * @param page
     * @param size
     * @return
     */
    @Override
    public ProductFeedPageResponse myFavorites(long uid, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long version = readVersion(uid);
        String key = myPageKey(uid, version, safeSize, safePage);

        ProductFeedPageResponse local = mineCache.getIfPresent(key);
        if (local != null) return local;

        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                ProductFeedPageResponse pageHit = objectMapper.readValue(
                        cached, ProductFeedPageResponse.class);
                ProductFeedPageResponse enriched = enrichPage(pageHit, uid);
                mineCache.put(key, enriched);
                return enriched;
            } catch (Exception ignored) {
            }
        }

        int offset = Math.multiplyExact(safePage - 1, safeSize);
        List<Long> ids = favoriteMapper.selectFavoriteProductIds(
                uid, offset, safeSize + 1);
        boolean hasMore = ids.size() > safeSize;
        if (hasMore) ids = ids.subList(0, safeSize);

        ProductFeedPageResponse result;
        if (ids.isEmpty()) {
            result = new ProductFeedPageResponse(
                    List.of(), safePage, safeSize, false);
            writeMine(key, result, true);
            return result;
        }

        List<ProductFeedRow> rows = productMapper.selectFeedByIds(ids);
        Map<Long, ProductFeedRow> rowMap = rows.stream().collect(
                Collectors.toMap(ProductFeedRow::getId, Function.identity()));
        List<ProductFeedRow> ordered = ids.stream()
                .map(rowMap::get).filter(Objects::nonNull).toList();

        result = enrichPage(new ProductFeedPageResponse(
                mapRows(ordered), safePage, safeSize, hasMore), uid);
        writeMine(key, result, false);
        return result;
    }

    /**
     * 将已覆盖计数和用户状态的收藏页写入 Redis 和本地缓存。
     * @param key
     * @param page
     * @param empty
     */
    private void writeMine(String key, ProductFeedPageResponse page,
                           boolean empty) {
        try {
            Duration ttl = empty
                    ? Duration.ofSeconds(10 + ThreadLocalRandom.current().nextInt(11))
                    : Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(20));
            redis.opsForValue().set(key,
                    objectMapper.writeValueAsString(page), ttl);
            mineCache.put(key, page);
        } catch (Exception e) {
            log.warn("我的商品收藏缓存写入失败: key={}", key, e);
        }
    }

    /**
     * 读取用户商品收藏版本。
     * @param uid
     * @return
     */
    private long readVersion(long uid) {
        String value = redis.opsForValue().get(myVersionKey(uid));
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            redis.delete(myVersionKey(uid));
            return 0L;
        }
    }

    /**
     * 为商品基础页面补充实时收藏信息。
     * @param page
     * @param uid
     * @return
     */
    private ProductFeedPageResponse enrichPage(ProductFeedPageResponse page,
                                                Long uid) {
        return new ProductFeedPageResponse(
                enrichItems(page.items(), uid),
                page.page(), page.size(), page.hasMore());
    }

    /**
     * 批量覆盖商品收藏计数及当前用户收藏状态。
     * @param base
     * @param uid
     * @return
     */
    private List<ProductFeedItemResponse> enrichItems(
            List<ProductFeedItemResponse> base, Long uid) {
        if (base.isEmpty()) return List.of();
        List<Long> ids = base.stream()
                .map(ProductFeedItemResponse::id).distinct().toList();

        Map<String, Map<String, Long>> counts = counterService.getCountsBatch(
                "product", ids.stream().map(String::valueOf).toList(),
                List.of("fav"));
        Map<Long, Boolean> states = uid == null
                ? Map.of()
                : stateReader.getBatch(ids, uid);

        return base.stream().map(item -> new ProductFeedItemResponse(
                item.id(), item.shopId(), item.createdBy(), item.name(),
                item.description(), item.images(), item.price(), item.stock(),
                item.status(), item.createTime(), item.updateTime(),
                item.creatorName(), item.creatorIcon(), item.shopName(),
                counts.getOrDefault(String.valueOf(item.id()), Map.of())
                        .getOrDefault("fav", 0L),
                Boolean.TRUE.equals(states.get(item.id()))
        )).toList();
    }

    /**
     * 将商品数据库行转换为基础条目列表。
     * @param rows
     * @return
     */
    private List<ProductFeedItemResponse> mapRows(List<ProductFeedRow> rows) {
        return rows.stream().map(this::mapRow).toList();
    }

    /**
     * 生成不含动态收藏信息的商品基础条目。
     * @param row
     * @return
     */
    private ProductFeedItemResponse mapRow(ProductFeedRow row) {
        return new ProductFeedItemResponse(
                row.getId(), row.getShopId(), row.getCreatedBy(),
                row.getName(), row.getDescription(), row.getImages(),
                row.getPrice(), row.getStock(), row.getStatus(),
                row.getCreateTime(), row.getUpdateTime(),
                row.getCreatorName(), row.getCreatorIcon(), row.getShopName(),
                0L, false);
    }

    /**
     * 定向清除公共商品页面和商品基础片段缓存。
     * @param productId
     */
    @Override
    public void invalidateProduct(long productId) {
        publicCache.asMap().entrySet().removeIf(entry -> entry.getValue().items()
                .stream().anyMatch(item -> item.id() == productId));
        long currentSlot = System.currentTimeMillis() / 3_600_000L;
        for (long slot : List.of(currentSlot, currentSlot - 1)) {
            String index = indexKey(productId, slot);
            Set<String> refs = redis.opsForSet().members(index);
            if (refs == null) continue;
            for (String ref : refs) {
                String[] parts = ref.split(":", 2);
                if (parts.length != 2) continue;
                try {
                    int size = Integer.parseInt(parts[0]);
                    int page = Integer.parseInt(parts[1]);
                    publicCache.invalidate(localKey(size, page));
                    String ids = idsKey(size, slot, page);
                    redis.delete(List.of(ids, hasMoreKey(ids)));
                } catch (NumberFormatException ignored) {
                }
            }
            redis.delete(index);
        }
        redis.delete(ITEM_PREFIX + productId);
    }
}
