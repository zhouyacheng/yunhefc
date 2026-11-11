package com.yunhefc.contracts.match;

/**
 * 存储比分（team_match.score_home/score_away）独立于事件推导比分。
 */
public record MatchScoreSnapshot(Integer scoreHome, Integer scoreAway, int version) {
}
