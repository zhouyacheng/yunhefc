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
}
