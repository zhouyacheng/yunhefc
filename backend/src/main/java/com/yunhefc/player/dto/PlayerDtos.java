package com.yunhefc.player.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
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

    public record PlayerConvertRequest(
            @Min(0) @Max(65535) Integer jersey) {
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

    public record PlayerView(
            Long id,
            String name,
            Integer jersey,
            Integer type,
            String typeLabel,
            Integer position,
            String positionLabel,
            List<Integer> secondaryPositions,
            List<String> secondaryPositionLabels,
            String region,
            String avatarUrl,
            Integer skill) {

        public static PlayerView from(PlayerAdminView view) {
            return new PlayerView(
                    view.id(),
                    view.name(),
                    view.jersey(),
                    view.type(),
                    view.typeLabel(),
                    view.position(),
                    view.positionLabel(),
                    view.secondaryPositions(),
                    view.secondaryPositionLabels(),
                    view.region(),
                    view.avatarUrl(),
                    view.skill());
        }
    }

    /**
     * 自助资料仅允许姓名、地区、主副位置与头像；其余字段由 {@link JsonAnySetter} 收集后拒绝。
     */
    @Getter
    @Setter
    public static class PlayerSelfUpdateRequest {

        @Size(max = 32)
        private String name;

        @Size(max = 64)
        private String region;

        @Min(0)
        @Max(3)
        private Integer position;

        @Size(max = 3)
        private List<@NotNull @Min(0) @Max(3) Integer> secondaryPositions;

        @Size(max = 512)
        private String avatarUrl;

        @JsonIgnore
        private final Set<String> unknownFields = new LinkedHashSet<>();

        @JsonAnySetter
        void addUnknownField(String field, JsonNode value) {
            if (!field.endsWith("Label") && !field.endsWith("Labels")) {
                unknownFields.add(field);
            }
        }
    }
}
