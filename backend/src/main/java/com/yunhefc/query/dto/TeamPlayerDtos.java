package com.yunhefc.query.dto;

import java.math.BigDecimal;
import java.util.List;

public final class TeamPlayerDtos {

    private TeamPlayerDtos() {
    }

    public record TeamPlayersView(List<TeamPlayerView> items, long total) {
    }

    public record TeamPlayerView(
            Long id,
            String name,
            Integer jersey,
            Integer position,
            String positionLabel,
            List<Integer> secondaryPositions,
            List<String> secondaryPositionLabels,
            String region,
            String avatarUrl,
            BigDecimal skill,
            Integer type,
            String typeLabel,
            int appearances) {
    }
}
