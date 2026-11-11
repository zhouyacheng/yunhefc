package com.yunhefc.contracts.registration;

import java.time.Instant;

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
        Instant registeredAt,
        Instant cancelledAt,
        boolean active) {
}
