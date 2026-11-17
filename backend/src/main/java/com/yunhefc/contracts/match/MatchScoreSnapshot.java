package com.yunhefc.contracts.match;

/**
 * 兜底比分（team_match.score_home/score_away）：仅无计分事件时作为最终比分，事件不物理回写该值。
 */
public record MatchScoreSnapshot(Integer scoreHome, Integer scoreAway, int version) {
}
