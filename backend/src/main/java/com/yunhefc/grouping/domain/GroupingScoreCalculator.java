package com.yunhefc.grouping.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分组综合分：手动星级与自动分按配置加权；先舍入再参与排序与落库。
 */
public final class GroupingScoreCalculator {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final int RATE_SCALE = 10;

    private GroupingScoreCalculator() {
    }

    public static List<GroupingScore> calculate(List<GroupingCandidate> candidates,
                                                Map<Long, GroupingPlayerStats> stats,
                                                GroupingWeights weights,
                                                int finishedMatches) {
        BigDecimal denominator = BigDecimal.valueOf(Math.max(finishedMatches, 1));
        BigDecimal maxGoalsPerGame = candidates.stream()
                .map(candidate -> statsOf(stats, candidate.playerId()))
                .map(playerStats -> perGame(playerStats.goals(), playerStats.appearances()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        BigDecimal maxAssistsPerGame = candidates.stream()
                .map(candidate -> statsOf(stats, candidate.playerId()))
                .map(playerStats -> perGame(playerStats.assists(), playerStats.appearances()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        List<GroupingScore> scores = new ArrayList<>(candidates.size());
        for (GroupingCandidate candidate : candidates) {
            GroupingPlayerStats playerStats = statsOf(stats, candidate.playerId());
            BigDecimal manual = BigDecimal.valueOf(Math.clamp(candidate.skill(), 1, 5))
                    .setScale(1, RoundingMode.HALF_UP);
            boolean manualOnly = playerStats.appearances() == 0
                    || playerStats.appearances() < weights.minAppearances();
            BigDecimal auto = null;
            BigDecimal finalScore = manual.setScale(2, RoundingMode.HALF_UP);
            if (playerStats.appearances() > 0) {
                BigDecimal appearanceRate = BigDecimal.valueOf(playerStats.appearances())
                        .divide(denominator, RATE_SCALE, RoundingMode.HALF_UP)
                        .min(BigDecimal.ONE);
                BigDecimal goalValue = normalize(perGame(playerStats.goals(),
                        playerStats.appearances()), maxGoalsPerGame);
                BigDecimal assistValue = normalize(perGame(playerStats.assists(),
                        playerStats.appearances()), maxAssistsPerGame);
                BigDecimal weighted = weights.appearanceRatio().multiply(appearanceRate)
                        .add(weights.goalRatio().multiply(goalValue))
                        .add(weights.assistRatio().multiply(assistValue));
                auto = BigDecimal.ONE.add(FOUR.multiply(weighted))
                        .setScale(2, RoundingMode.HALF_UP);
                if (!manualOnly) {
                    finalScore = manual.multiply(weights.manualRatio())
                            .add(auto.multiply(weights.autoRatio()))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
            scores.add(new GroupingScore(candidate.playerId(), manual, auto, finalScore, manualOnly));
        }
        return scores;
    }

    private static GroupingPlayerStats statsOf(Map<Long, GroupingPlayerStats> stats, Long playerId) {
        return stats.getOrDefault(playerId, GroupingPlayerStats.EMPTY);
    }

    private static BigDecimal perGame(int value, int appearances) {
        if (appearances <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(appearances),
                RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalize(BigDecimal value, BigDecimal poolMax) {
        if (poolMax.subtract(BigDecimal.ZERO).abs().compareTo(new BigDecimal("0.000000001")) < 0) {
            return HALF;
        }
        return value.divide(poolMax, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
