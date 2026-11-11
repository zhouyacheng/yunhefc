package com.yunhefc.contracts.attendance;

import java.time.Instant;

/**
 * 跨模块读取的考勤快照。
 */
public record AttendanceSnapshot(
        Long id,
        Long playerId,
        Integer status,
        String statusLabel,
        Integer source,
        String sourceLabel,
        Instant recordedAt) {
}
