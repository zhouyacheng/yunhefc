package com.yunhefc.stats.dto;

import java.math.BigDecimal;
import java.util.List;

public final class LeaderboardDtos {

    private LeaderboardDtos() {
    }

    public record LeaderboardView(String season, String scope, List<LeaderboardEntry> items) {
    }

    /**
     * 扁平 9 项指标与 LeaderboardTotals 一一对应；05 契约固定为扁平字段，避免客户端破坏性变更。
     */
    public record LeaderboardEntry(
            Long playerId,
            String name,
            Integer jersey,
            Integer position,
            String avatarUrl,
            int appearances,
            int goals,
            int assists,
            int ownGoals,
            int internalAppearances,
            int internalWins,
            BigDecimal winRate,
            BigDecimal goalsPerMatch,
            BigDecimal assistsPerMatch,
            LeaderboardTotals history,
            LeaderboardTotals career) {
    }

    public record LeaderboardTotals(
            int appearances,
            int goals,
            int assists,
            int ownGoals,
            int internalAppearances,
            int internalWins,
            BigDecimal winRate,
            BigDecimal goalsPerMatch,
            BigDecimal assistsPerMatch) {
    }
}
