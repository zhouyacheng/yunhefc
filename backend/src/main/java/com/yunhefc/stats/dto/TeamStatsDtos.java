package com.yunhefc.stats.dto;

import java.time.Instant;
import java.util.List;

public final class TeamStatsDtos {

    private TeamStatsDtos() {
    }

    public record TeamStatsView(String season, List<PlayerStatsView> items) {
    }

    public record PlayerStatsView(
            Long playerId,
            String season,
            int appearances,
            int goals,
            int assists,
            Instant updatedAt) {
    }
}
