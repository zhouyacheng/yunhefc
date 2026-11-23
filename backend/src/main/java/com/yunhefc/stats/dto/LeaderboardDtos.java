package com.yunhefc.stats.dto;

import java.math.BigDecimal;
import java.util.List;

public final class LeaderboardDtos {

    private LeaderboardDtos() {
    }

    public record LeaderboardView(String season, String scope, List<LeaderboardEntry> items) {
    }

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
            BigDecimal assistsPerMatch) {
    }
}
