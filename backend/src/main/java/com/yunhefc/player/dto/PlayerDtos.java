package com.yunhefc.player.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class PlayerDtos {

    private PlayerDtos() {
    }

    public record PlayerAdminQuery(
            String name,
            String region,
            Integer type,
            Boolean admin,
            Integer status,
            Integer position,
            Integer skill,
            String sort,
            long page,
            long pageSize) {
    }

    public record PlayerAdminView(
            Long id,
            String name,
            Integer jersey,
            Integer position,
            String positionLabel,
            List<Integer> secondaryPositions,
            List<String> secondaryPositionLabels,
            String region,
            String avatarUrl,
            Integer skill,
            Integer type,
            String typeLabel,
            Long userId,
            String phoneMasked,
            Integer status,
            String statusLabel,
            boolean admin,
            Set<String> roles,
            Instant createdAt) {
    }

    public record PlayerUpdateRequest(
            @Size(max = 32) String name,
            @Min(0) @Max(65535) Integer jersey,
            @Size(max = 64) String region,
            @Min(0) @Max(3) Integer position,
            @Size(max = 3) List<@NotNull @Min(0) @Max(3) Integer> secondaryPositions,
            @Min(1) @Max(5) Integer skill,
            @Size(max = 512) String avatarUrl) {
    }
}
