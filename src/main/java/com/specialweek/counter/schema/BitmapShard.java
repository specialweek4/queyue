package com.specialweek.counter.schema;

public final class BitmapShard {

    public static final int CHUNK_SIZE = 32_768;

    public static long chunkOf(long userId) {
        return userId / CHUNK_SIZE;
    }

    public static long bitOf(long userId) {
        return userId % CHUNK_SIZE;
    }

    private BitmapShard() {
    }
}
