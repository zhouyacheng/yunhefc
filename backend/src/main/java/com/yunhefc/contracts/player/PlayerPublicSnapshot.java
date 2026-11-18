package com.yunhefc.contracts.player;

import java.math.BigDecimal;
import java.util.List;

/**
 * 球队页公开可见的球员快照；仅包含公开字段。
 */
public record PlayerPublicSnapshot(
        Long id,
        String name,
        Integer jersey,
        Integer position,
        List<Integer> secondaryPositions,
        String region,
        String avatarUrl,
        BigDecimal skill,
        Integer type) {
}
