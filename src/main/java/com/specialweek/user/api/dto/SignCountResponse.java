package com.specialweek.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 签到状态返回
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignCountResponse {

    /** 今日是否已签到 */
    private boolean today;

    /**
     * 连续签到天数：
     * 今日已签时为截至今天的连续天数；今日未签时为截至昨天的连续天数（避免"一夜清零"的观感）
     */
    private int streak;

    /** 本月累计已签天数（BITCOUNT 统计，断签不清零） */
    private int monthDays;
}
