package com.yunhefc.grouping.domain;

import java.math.BigDecimal;

/**
 * 单个候选的手动分、自动分与综合分；autoScore 在无出场时为 null。
 */
public record GroupingScore(
        Long playerId,
        BigDecimal manualScore,
        BigDecimal autoScore,
        BigDecimal finalScore,
        boolean manualOnly) {
}
