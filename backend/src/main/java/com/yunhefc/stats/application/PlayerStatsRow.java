package com.yunhefc.stats.application;

import com.yunhefc.stats.domain.PlayerStatsSummary;

import java.time.LocalDateTime;

/**
 * 某赛季球员统计汇总行。
 */
public record PlayerStatsRow(
        Long playerId,
        String season,
        int appearances,
        int goals,
        int assists,
        int ownGoals,
        int internalAppearances,
        int internalWins,
        LocalDateTime updatedAt) {

    public static PlayerStatsRow of(PlayerStatsSummary summary, LocalDateTime updatedAt) {
        return new PlayerStatsRow(
                summary.getPlayerId(),
                summary.getSeason(),
                summary.getAppearances(),
                summary.getGoals(),
                summary.getAssists(),
                summary.getOwnGoals(),
                summary.getInternalAppearances(),
                summary.getInternalWins(),
                updatedAt);
    }
}
