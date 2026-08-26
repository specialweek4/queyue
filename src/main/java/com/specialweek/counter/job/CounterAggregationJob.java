package com.specialweek.counter.job;

import com.specialweek.counter.schema.CounterKeys;
import com.specialweek.counter.schema.CounterSchema;
import com.specialweek.counter.util.RedisScanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CounterAggregationJob {

    private static final String AGG_PATTERN = "agg:v1:*";
    private static final int SCAN_BATCH = 200;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;
    private final DefaultRedisScript<Long> decrScript;

    public CounterAggregationJob(StringRedisTemplate redis) {
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA);
        this.decrScript = new DefaultRedisScript<>();
        this.decrScript.setResultType(Long.class);
        this.decrScript.setScriptText(DECR_FIELD_LUA);
    }

    @Scheduled(fixedDelayString = "${queyue.counter.flush-ms:1000}",
            initialDelayString = "${queyue.counter.flush-initial-ms:3000}")
    public void flush() {
        List<String> aggKeys = RedisScanUtil.scanKeysOrEmpty(redis, AGG_PATTERN, SCAN_BATCH);
        for (String aggKey : aggKeys) {
            try {
                foldOne(aggKey);
            } catch (RuntimeException e) {
                log.error("聚合桶折叠失败，下轮重试: {}", aggKey, e);
            }
        }
    }

    private void foldOne(String aggKey) {
        Map<Object, Object> entries = redis.opsForHash().entries(aggKey);
        if (entries.isEmpty()) {
            return;
        }
        String[] parts = aggKey.split(":", 4);
        if (parts.length < 4) {
            return;
        }
        String cntKey = CounterKeys.sdsKey(parts[2], parts[3]);

        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            String field = String.valueOf(e.getKey());
            long delta;
            try {
                delta = Long.parseLong(String.valueOf(e.getValue()));
            } catch (NumberFormatException nfe) {
                continue;
            }
            if (delta == 0) {
                continue;
            }
            int idx;
            try {
                idx = Integer.parseInt(field);
            } catch (NumberFormatException nfe) {
                continue;
            }
            try {
                redis.execute(incrScript, List.of(cntKey),
                        String.valueOf(CounterSchema.SCHEMA_LEN),
                        String.valueOf(CounterSchema.FIELD_SIZE),
                        String.valueOf(idx),
                        String.valueOf(delta));

                redis.execute(decrScript, List.of(aggKey), field, String.valueOf(delta));
            } catch (Exception ex) {
                // 留存字段，下一轮重试
            }
        }
        Long size = redis.opsForHash().size(aggKey);
        if (size != null && size == 0L) {
            redis.delete(aggKey);
        }
    }

    private static final String INCR_FIELD_LUA = """
            local cntKey = KEYS[1]
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2]) -- 固定为4
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            local function read32be(s, off)
              local b = {string.byte(s, off+1, off+4)}
              local n = 0
              for i=1,4 do n = n * 256 + b[i] end
              return n
            end
            local function write32be(n)
              local t = {}
              for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end
              return string.char(unpack(t))
            end
            local cnt = redis.call('GET', cntKey)
            if not cnt then cnt = string.rep(string.char(0), schemaLen * fieldSize) end
            local off = idx * fieldSize
            local v = read32be(cnt, off) + delta
            if v < 0 then v = 0 end
            local seg = write32be(v)
            cnt = string.sub(cnt, 1, off) .. seg .. string.sub(cnt, off+fieldSize+1)
            redis.call('SET', cntKey, cnt)
            return 1
            """;

    private static final String DECR_FIELD_LUA = """
            local key = KEYS[1]
            local field = ARGV[1]
            local delta = tonumber(ARGV[2])
            local v = redis.call('HINCRBY', key, field, -delta)
            if v == 0 then
                redis.call('HDEL', key, field)
            end
            return v
            """;
}
