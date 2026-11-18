package com.yunhefc.contracts.registration;

import java.time.LocalDateTime;

/**
 * 跨模块读取的报名快照；active 标识是否仍为有效报名（CONFIRMED/WAITLISTED）。
 */
public record RegistrationSnapshot(
        Long id,
        Long matchId,
        Long playerId,
        Integer type,
        String typeLabel,
        Integer status,
        String statusLabel,
        LocalDateTime registeredAt,
        LocalDateTime cancelledAt,
        boolean active) {

    /**
     * 已确认报名：仅 CONFIRMED 可自助签到与进入考勤统计。
     */
    public boolean confirmed() {
        return status != null && status == RegistrationStatusCodes.CONFIRMED;
    }
}
