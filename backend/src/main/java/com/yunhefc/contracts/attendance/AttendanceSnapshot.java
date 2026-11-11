package com.yunhefc.contracts.attendance;

import java.time.Instant;

/**
 * 跨模块读取的考勤快照。
 */
public record AttendanceSnapshot(
        Long id,
        Long matchId,
        Long playerId,
        Integer status,
        String statusLabel,
        Integer source,
        String sourceLabel,
        Instant recordedAt) {

    private static final int PRESENT = 0;
    private static final int TEMP_PRESENT = 2;

    /**
     * 出场口径：正常到场或临时到场；缺勤不计。
     */
    public boolean countsAsAppearance() {
        return status != null && (status == PRESENT || status == TEMP_PRESENT);
    }
}
