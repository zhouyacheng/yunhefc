package com.yunhefc.grouping.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 分组分数直接使用球员档案星级（1.00–5.00 两位小数），不再计算自动分与加权综合分。
 */
public final class GroupingScoreCalculator {

    private static final BigDecimal MIN_SKILL = new BigDecimal("1.00");
    private static final BigDecimal MAX_SKILL = new BigDecimal("5.00");
    private static final BigDecimal DEFAULT_SKILL = new BigDecimal("3.00");

    private GroupingScoreCalculator() {
    }

    public static List<GroupingScore> calculate(List<GroupingCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> {
                    BigDecimal skill = candidate.skill() == null ? DEFAULT_SKILL : candidate.skill();
                    BigDecimal score = skill.max(MIN_SKILL).min(MAX_SKILL)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new GroupingScore(candidate.playerId(), score, null, score);
                })
                .toList();
    }
}
