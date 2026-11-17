package com.yunhefc.contracts.event;

/**
 * 计分事件（进球/乌龙）推导比分快照。
 */
public record DerivedScoreSnapshot(int scoreHome, int scoreAway) {
}
