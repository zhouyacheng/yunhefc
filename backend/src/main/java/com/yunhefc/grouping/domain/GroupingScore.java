package com.yunhefc.grouping.domain;

import java.math.BigDecimal;

/**
 * 单个候选的分组分数：新生成时 manualScore/finalScore 均为球员星级，autoScore 为 null；历史行保留原值。
 */
public record GroupingScore(
        Long playerId,
        BigDecimal manualScore,
        BigDecimal autoScore,
        BigDecimal finalScore) {
}
