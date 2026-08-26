package com.specialweek.counter.service.impl;

import com.specialweek.counter.schema.BitmapShard;
import com.specialweek.counter.schema.CounterKeys;
import com.specialweek.counter.schema.CounterSchema;
import com.specialweek.counter.service.CounterService;
import com.specialweek.counter.util.RedisScanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CounterServiceImpl implements CounterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> toggleScript;
    private final DefaultRedisScript<List> readScript;

    public CounterServiceImpl(
            StringRedisTemplate redis,
            @Qualifier("counterReadScript") DefaultRedisScript<List> counterReadScript) {
        this.redis = redis;
        this.readScript = counterReadScript;
        this.toggleScript = new DefaultRedisScript<>();
        this.toggleScript.setResultType(Long.class);
        this.toggleScript.setScriptText(TOGGLE_LUA);
    }

    @Override
    public boolean like(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, true);
    }

    @Override
    public boolean unlike(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "like", CounterSchema.IDX_LIKE, false);
    }

    @Override
    public boolean fav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, true);
    }

    @Override
    public boolean unfav(String entityType, String entityId, long userId) {
        return toggle(entityType, entityId, userId, "fav", CounterSchema.IDX_FAV, false);
    }

    private boolean toggle(String etype, String eid, long uid, String metric, int idx, boolean add) {
        long chunk = BitmapShard.chunkOf(uid);
        long bit = BitmapShard.bitOf(uid);
        String bmKey = CounterKeys.bitmapKey(metric, etype, eid, chunk);
        List<String> keys = List.of(bmKey);
        List<String> args = List.of(String.valueOf(bit), add ? "add" : "remove");
        Long changed = redis.execute(toggleScript, keys, args.toArray());
        boolean ok = changed != null && changed == 1L;
        if (ok) {
            long delta = add ? 1L : -1L;
            // 同步写聚合桶（接入 Kafka 后可改为事件异步聚合）
            redis.opsForHash().increment(CounterKeys.aggKey(etype, eid), String.valueOf(idx), delta);
            // 脏版本：MySQL 检查点任务据此同步 tb_blog
            redis.opsForValue().increment(CounterKeys.dirtyKey(etype, eid));
        }
        return ok;
    }

    @Override
    public Map<String, Long> getCounts(String entityType, String entityId, List<String> metrics) {
        List<Long> logical = readLogical(entityType, entityId);
        if (logical.size() != 2 || logical.get(0) < 0) {
            rebuild(entityType, entityId);
            logical = readLogical(entityType, entityId);
        }
        Map<String, Long> result = new LinkedHashMap<>();
        if (metrics != null) {
            for (String m : metrics) {
                Integer idx = CounterSchema.NAME_TO_IDX.get(m);
                if (idx == null) {
                    continue;
                }
                long val = logical.size() == 2
                        ? (idx == CounterSchema.IDX_LIKE ? logical.get(0) : logical.get(1))
                        : 0L;
                result.put(m, Math.max(0, val));
            }
        }
        return result;
    }

    @Override
    public Map<String, Map<String, Long>> getCountsBatch(String entityType, List<String> entityIds, List<String> metrics) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (entityIds == null || entityIds.isEmpty() || metrics == null || metrics.isEmpty()) {
            return out;
        }
        List<String> distinct = entityIds.stream().distinct().toList();
        String sha = readScript.getSha1();
        String scriptText = readScript.getScriptAsString();

        List<Object> rawResults;
        try {
            rawResults = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (String eid : distinct) {
                    connection.scriptingCommands().evalSha(sha, ReturnType.MULTI, 2,
                            keysAndArgs(entityType, eid));
                }
                return null;
            });
        } catch (RuntimeException e) {
            if (containsNoScript(e)) {
                loadScript(scriptText);
                rawResults = redis.executePipelined((RedisCallback<Object>) connection -> {
                    for (String eid : distinct) {
                        connection.scriptingCommands().evalSha(sha, ReturnType.MULTI, 2,
                                keysAndArgs(entityType, eid));
                    }
                    return null;
                });
            } else {
                throw e;
            }
        }
        if (rawResults == null) {
            throw new IllegalStateException("计数批量读取失败");
        }

        for (int i = 0; i < distinct.size(); i++) {
            String eid = distinct.get(i);
            List<Long> pair = toLongList(i < rawResults.size() ? rawResults.get(i) : null);
            Map<String, Long> m = new LinkedHashMap<>();
            if (pair.size() == 2 && pair.get(0) >= 0) {
                for (String name : metrics) {
                    Integer idx = CounterSchema.NAME_TO_IDX.get(name);
                    if (idx == null) {
                        continue;
                    }
                    m.put(name, Math.max(0, idx == CounterSchema.IDX_LIKE ? pair.get(0) : pair.get(1)));
                }
            } else {
                if (pair.size() == 2 && pair.get(0) == -2L) {
                    log.error("计数结构异常，按零值降级: {}:{}", entityType, eid);
                }
                for (String name : metrics) {
                    if (CounterSchema.NAME_TO_IDX.containsKey(name)) {
                        m.put(name, 0L);
                    }
                }
            }
            out.put(eid, m);
        }
        return out;
    }

    @Override
    public boolean isLiked(String entityType, String entityId, long userId) {
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);
        return getBit(CounterKeys.bitmapKey("like", entityType, entityId, chunk), bit);
    }

    @Override
    public boolean isFaved(String entityType, String entityId, long userId) {
        long chunk = BitmapShard.chunkOf(userId);
        long bit = BitmapShard.bitOf(userId);
        return getBit(CounterKeys.bitmapKey("fav", entityType, entityId, chunk), bit);
    }

    private boolean getBit(String key, long offset) {
        Boolean bit = redis.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().getBit(key.getBytes(StandardCharsets.UTF_8), offset));
        return Boolean.TRUE.equals(bit);
    }

    private List<Long> readLogical(String entityType, String entityId) {
        List<?> raw = redis.execute(readScript,
                List.of(CounterKeys.sdsKey(entityType, entityId), CounterKeys.aggKey(entityType, entityId)));
        return toLongList(raw);
    }

    private void rebuild(String entityType, String entityId) {
        log.warn("计数结构缺失或异常，基于位图分片重建: {}:{}", entityType, entityId);
        byte[] buf = new byte[CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE];
        for (String metric : CounterSchema.NAME_TO_IDX.keySet()) {
            int idx = CounterSchema.NAME_TO_IDX.get(metric);
            long sum = bitCountShards(metric, entityType, entityId);
            writeInt32BE(buf, idx * CounterSchema.FIELD_SIZE, sum);
        }
        setRaw(CounterKeys.sdsKey(entityType, entityId), buf);
        redis.opsForHash().delete(CounterKeys.aggKey(entityType, entityId), "1", "2");
    }

    private long bitCountShards(String metric, String entityType, String entityId) {
        String pattern = String.format("bm:%s:%s:%s:*", metric, entityType, entityId);
        List<String> keys = RedisScanUtil.scanAll(redis, pattern);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        List<Object> res = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (String k : keys) {
                connection.stringCommands().bitCount(k.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        long sum = 0L;
        if (res != null) {
            for (Object o : res) {
                if (o instanceof Number n) {
                    sum += n.longValue();
                }
            }
        }
        return sum;
    }

    private byte[] getRaw(String key) {
        return redis.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(key.getBytes(StandardCharsets.UTF_8)));
    }


    private void setRaw(String key, byte[] val) {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.stringCommands().set(key.getBytes(StandardCharsets.UTF_8), val);
            return null;
        });
    }

    private static byte[][] keysAndArgs(String entityType, String entityId) {
        byte[][] keysAndArgs = new byte[2][];
        keysAndArgs[0] = CounterKeys.sdsKey(entityType, entityId).getBytes(StandardCharsets.UTF_8);
        keysAndArgs[1] = CounterKeys.aggKey(entityType, entityId).getBytes(StandardCharsets.UTF_8);
        return keysAndArgs;
    }

    private void loadScript(String scriptText) {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.scriptingCommands().scriptLoad(scriptText.getBytes(StandardCharsets.UTF_8));
            return null;
        });
    }

    private static boolean containsNoScript(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (msg != null && msg.toUpperCase().contains("NOSCRIPT")) {
                return true;
            }
        }
        return false;
    }

    private static long readInt32BE(byte[] buf, int off) {
        long n = 0;
        for (int i = 0; i < 4; i++) {
            n = (n << 8) | (buf[off + i] & 0xFFL);
        }
        return n;
    }

    private static void writeInt32BE(byte[] buf, int off, long val) {
        long n = Math.max(0, Math.min(val, 0xFFFF_FFFFL));
        buf[off] = (byte) ((n >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((n >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((n >>> 8) & 0xFF);
        buf[off + 3] = (byte) (n & 0xFF);
    }

    static List<Long> toLongList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>(list.size());
        for (Object o : list) {
            out.add(toLong(o));
        }
        return out;
    }

    static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof byte[] b) {
            return Long.parseLong(new String(b, StandardCharsets.UTF_8).trim());
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("计数返回值无法解析: " + o, e);
        }
    }

    private static final String TOGGLE_LUA = """
            local bmKey = KEYS[1]
            local offset = tonumber(ARGV[1])
            local op = ARGV[2] -- 'add' or 'remove'
            local prev = redis.call('GETBIT', bmKey, offset)
            if op == 'add' then
              if prev == 1 then return 0 end
              redis.call('SETBIT', bmKey, offset, 1)
              return 1
            elseif op == 'remove' then
              if prev == 0 then return 0 end
              redis.call('SETBIT', bmKey, offset, 0)
              return 1
            end
            return -1
            """;
}
