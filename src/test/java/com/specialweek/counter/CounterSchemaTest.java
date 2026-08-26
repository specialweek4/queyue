package com.specialweek.counter;

import com.specialweek.counter.schema.BitmapShard;
import com.specialweek.counter.schema.CounterKeys;
import com.specialweek.counter.schema.CounterSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CounterSchemaTest {

    @Test
    void userId70000MapsToChunk2Bit4464() {
        assertEquals(2L, BitmapShard.chunkOf(70000L));
        assertEquals(4464L, BitmapShard.bitOf(70000L));
    }

    @Test
    void chunkBoundaryMapping() {
        assertEquals(0L, BitmapShard.chunkOf(1L));
        assertEquals(0L, BitmapShard.chunkOf(32767L));
        assertEquals(1L, BitmapShard.chunkOf(32768L));
        assertEquals(1L, BitmapShard.chunkOf(32769L));
        assertEquals(32767L, BitmapShard.bitOf(32767L));
        assertEquals(0L, BitmapShard.bitOf(32768L));
    }

    @Test
    void schemaLayout() {
        assertEquals(4, CounterSchema.FIELD_SIZE);
        assertEquals(5, CounterSchema.SCHEMA_LEN);
        assertEquals(20, CounterSchema.SCHEMA_LEN * CounterSchema.FIELD_SIZE);
        assertEquals(1, CounterSchema.IDX_LIKE);
        assertEquals(2, CounterSchema.IDX_FAV);
        assertEquals(1, CounterSchema.NAME_TO_IDX.get("like"));
        assertEquals(2, CounterSchema.NAME_TO_IDX.get("fav"));
        assertTrue(CounterSchema.SUPPORTED_METRICS.contains("like"));
        assertTrue(CounterSchema.SUPPORTED_METRICS.contains("fav"));
    }

    @Test
    void keyFormats() {
        assertEquals("cnt:v1:blog:123", CounterKeys.sdsKey("blog", "123"));
        assertEquals("bm:like:blog:123:2", CounterKeys.bitmapKey("like", "blog", "123", 2L));
        assertEquals("agg:v1:blog:123", CounterKeys.aggKey("blog", "123"));
        assertEquals("dirty:v1:blog:123", CounterKeys.dirtyKey("blog", "123"));
    }
}
