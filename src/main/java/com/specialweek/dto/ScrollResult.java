package com.specialweek.dto;

import lombok.Data;

import java.util.List;

/**
 * @author specialweek
 * @since 2026-08-15
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
