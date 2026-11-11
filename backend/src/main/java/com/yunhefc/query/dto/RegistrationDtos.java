package com.yunhefc.query.dto;

import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.registration.RegistrationSnapshot;

import java.time.Instant;

/**
 * REG-03/04/05/06 共用的报名行结构。
 */
public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record RegistrationRow(
            Long id,
            PlayerRef player,
            Integer type,
            String typeLabel,
            Integer status,
            String statusLabel,
            Instant registeredAt,
            Instant cancelledAt) {

        public static RegistrationRow from(RegistrationSnapshot registration, PlayerSnapshot player) {
            Position position = Position.of(player.position());
            return new RegistrationRow(
                    registration.id(),
                    new PlayerRef(player.id(), player.name(), player.jersey(),
                            position.code(), position.label()),
                    registration.type(),
                    registration.typeLabel(),
                    registration.status(),
                    registration.statusLabel(),
                    registration.registeredAt(),
                    registration.cancelledAt());
        }
    }

    public record PlayerRef(
            Long id,
            String name,
            Integer jersey,
            Integer position,
            String positionLabel) {
    }
}
