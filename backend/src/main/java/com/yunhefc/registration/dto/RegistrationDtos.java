package com.yunhefc.registration.dto;

import java.time.Instant;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record RegistrationView(
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

    public record RegistrationResponse(RegistrationView registration) {
    }

    /**
     * 取消结果；未触发递补时 promotedPlayerId 为 null。
     */
    public record CancellationResult(boolean cancelled, Long promotedPlayerId, boolean promoted) {
    }

    /**
     * 管理员移除结果；未触发递补时 promotedPlayerId 为 null。
     */
    public record RemovalResult(boolean removed, Long promotedPlayerId, boolean promoted) {
    }
}
