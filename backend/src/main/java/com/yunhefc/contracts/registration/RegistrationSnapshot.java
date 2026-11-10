package com.yunhefc.contracts.registration;

import java.time.Instant;

/**
 * 跨模块读取的本人报名快照，仅含有效报名（CONFIRMED/WAITLISTED）。
 */
public record RegistrationSnapshot(
        Long id,
        Long playerId,
        Integer type,
        String typeLabel,
        Integer status,
        String statusLabel,
        Instant registeredAt,
        Instant cancelledAt,
        boolean active) {
}
