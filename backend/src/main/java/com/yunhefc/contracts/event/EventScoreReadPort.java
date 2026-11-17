package com.yunhefc.contracts.event;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 跨模块读取比赛的事件推导比分；仅存在有效进球/乌龙事件时返回值，无计分事件时为空。
 */
public interface EventScoreReadPort {

    Optional<DerivedScoreSnapshot> findDerivedScore(Long matchId);

    /**
     * 批量读取；只返回存在有效计分事件的比赛，缺失键表示无计分事件。
     */
    Map<Long, DerivedScoreSnapshot> findDerivedScores(Collection<Long> matchIds);
}
