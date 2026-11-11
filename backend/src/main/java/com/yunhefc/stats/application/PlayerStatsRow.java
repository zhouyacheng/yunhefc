package com.yunhefc.stats.application;

import com.yunhefc.stats.domain.PlayerStatsSummary;

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

    public static PlayerStatsRow of(PlayerStatsSummary summary, Instant updatedAt) {
        return new PlayerStatsRow(
                summary.getPlayerId(),
                summary.getSeason(),
                summary.getAppearances(),
                summary.getGoals(),
                summary.getAssists(),
                updatedAt);
    }
}
