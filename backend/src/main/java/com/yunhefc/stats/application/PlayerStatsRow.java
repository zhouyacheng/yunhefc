package com.yunhefc.stats.application;

import java.time.Instant;

/**
 * 某赛季球员统计汇总行。
 */
public record PlayerStatsRow(
        Long playerId,
        String season,
        int appearances,
        int goals,
        int assists,
        Instant updatedAt) {
}
