package com.yunhefc.contracts.event;

import java.util.Optional;

/**
 * 跨模块读取比赛的事件推导比分；无有效事件时返回空。
 */
public interface EventScoreReadPort {

    Optional<DerivedScoreSnapshot> findDerivedScore(Long matchId);
}
