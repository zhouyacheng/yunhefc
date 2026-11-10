package com.yunhefc.contracts.player;

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
        Integer skill,
        Integer type) {
}
