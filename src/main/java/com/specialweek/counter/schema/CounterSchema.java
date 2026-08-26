package com.specialweek.counter.schema;

import java.util.Map;
import java.util.Set;

public final class CounterSchema {

    // v1 Schema 下标约定（可扩展）
    // 0: read（预留）
    // 1: like
    // 2: fav
    // 3: comment（预留）
    // 4: repost（预留）
    public static final String SCHEMA_ID = "v1";
    public static final int FIELD_SIZE = 4;
    public static final int SCHEMA_LEN = 5;

    public static final int IDX_LIKE = 1;
    public static final int IDX_FAV = 2;

    public static final Map<String, Integer> NAME_TO_IDX = Map.of(
            "like", IDX_LIKE,
            "fav", IDX_FAV
    );

    public static final Set<String> SUPPORTED_METRICS = NAME_TO_IDX.keySet();

    private CounterSchema() {
    }
}
