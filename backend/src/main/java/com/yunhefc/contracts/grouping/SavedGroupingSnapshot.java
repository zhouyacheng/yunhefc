package com.yunhefc.contracts.grouping;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 比赛已保存分组的只读快照；playerTeams 值 0=A 队、1=B 队。
 */
public record SavedGroupingSnapshot(
        Long matchId,
        LocalDateTime savedAt,
        Map<Long, Integer> playerTeams) {
}
