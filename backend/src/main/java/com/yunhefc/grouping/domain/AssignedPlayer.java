package com.yunhefc.grouping.domain;

/**
 * 已计算综合分并分配到某一队的候选。
 */
public record AssignedPlayer(GroupingCandidate candidate, GroupingScore score) {
}
