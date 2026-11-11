package com.yunhefc.contracts.match;

/**
 * 比赛存储比分的前后快照，用于事件模块写比分并记录审计。
 */
public record MatchScoreChange(MatchScoreSnapshot before, MatchScoreSnapshot after) {
}
