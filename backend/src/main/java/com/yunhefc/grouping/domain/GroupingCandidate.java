package com.yunhefc.grouping.domain;

import java.math.BigDecimal;

/**
 * 通过名单、球员与账号过滤后的分组候选。
 */
public record GroupingCandidate(
        Long playerId,
        String name,
        Integer jersey,
        int position,
        BigDecimal skill) {
}
