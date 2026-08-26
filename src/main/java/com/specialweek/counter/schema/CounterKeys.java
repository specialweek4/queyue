package com.specialweek.counter.schema;

public final class CounterKeys {

    private CounterKeys() {
    }

    public static String sdsKey(String entityType, String entityId) {
        return String.format("cnt:%s:%s:%s", CounterSchema.SCHEMA_ID, entityType, entityId);
    }

    public static String bitmapKey(String metric, String entityType, String entityId, long chunk) {
        return String.format("bm:%s:%s:%s:%d", metric, entityType, entityId, chunk);
    }

    public static String aggKey(String entityType, String entityId) {
        return String.format("agg:%s:%s:%s", CounterSchema.SCHEMA_ID, entityType, entityId);
    }

    public static String dirtyKey(String entityType, String entityId) {
        return String.format("dirty:%s:%s:%s", CounterSchema.SCHEMA_ID, entityType, entityId);
    }
}
