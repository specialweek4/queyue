package com.specialweek.common.util;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author specialweek
 * @since 2026-08-15
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
