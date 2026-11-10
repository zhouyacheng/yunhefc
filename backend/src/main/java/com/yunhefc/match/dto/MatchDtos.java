package com.yunhefc.match.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MatchDtos {

    private MatchDtos() {
    }

    public record MatchAdminQuery(
            Integer status,
            Integer type,
            String season,
            String scope,
            String sort,
            long page,
            long pageSize) {
    }

    public record MatchPublicQuery(
            Integer type,
            String venue,
            Integer status,
            String season,
            String month,
            String scope,
            String sort,
            long page,
            long pageSize) {
    }

    public record MatchCreateRequest(
            @NotBlank @Size(max = 128) String title,
            @NotNull @Min(0) @Max(3) Integer type,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotBlank @Size(max = 128) String venue,
            @NotBlank @Size(max = 64) String homeTeamName,
            @NotBlank @Size(max = 64) String awayTeamName,
            @Size(max = 16) String format,
            @Size(max = 64) String competition,
            String season,
            @Min(0) @Max(65535) Integer round,
            @Min(0) @Max(1) Integer ourSide,
            @Min(1) @Max(65535) Integer capacity,
            @Min(0) @Max(65535) Integer guestCapacity,
            @Min(0) @Max(65535) Integer waitlistCapacity,
            Boolean includeGuests,
            @Min(0) @Max(255) Integer scoreHome,
            @Min(0) @Max(255) Integer scoreAway) {
    }

    public record MatchStatusRequest(
            @NotNull @Min(0) @Max(6) Integer status,
            @NotNull @Min(0) Integer version,
            Boolean manualStatus) {
    }

    /**
     * 编辑比赛仅允许赛程字段；赛季/类型/我方主客/比分等由 {@link JsonAnySetter} 收集后拒绝。
     * `round`、`competition` 可为空，用显式 null 清空，故单独记录字段是否出现。
     */
    @Getter
    @Setter
    public static class MatchUpdateRequest {

        @Size(max = 128)
        private String title;

        private Instant startAt;

        private Instant endAt;

        @Size(max = 128)
        private String venue;

        @Size(max = 64)
        private String competition;

        @Min(0)
        @Max(65535)
        private Integer round;

        @Size(max = 64)
        private String homeTeamName;

        @Size(max = 64)
        private String awayTeamName;

        @Min(1)
        @Max(65535)
        private Integer capacity;

        @Min(0)
        @Max(65535)
        private Integer guestCapacity;

        @Min(0)
        @Max(65535)
        private Integer waitlistCapacity;

        private Boolean includeGuests;

        @NotNull
        @Min(0)
        private Integer version;

        @JsonIgnore
        private final Set<String> presentFields = new LinkedHashSet<>();

        @JsonIgnore
        private final Set<String> unknownFields = new LinkedHashSet<>();

        @JsonProperty("competition")
        public void setCompetition(String competition) {
            presentFields.add("competition");
            this.competition = competition;
        }

        @JsonProperty("round")
        public void setRound(Integer round) {
            presentFields.add("round");
            this.round = round;
        }

        @JsonIgnore
        public boolean hasCompetition() {
            return presentFields.contains("competition");
        }

        @JsonIgnore
        public boolean hasRound() {
            return presentFields.contains("round");
        }

        @JsonAnySetter
        void addUnknownField(String field, JsonNode value) {
            unknownFields.add(field);
        }
    }

    public record MatchAdminView(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            String format,
            Instant startAt,
            Instant endAt,
            Instant signupDeadline,
            Instant cancelLock,
            String venue,
            String competition,
            String season,
            Integer round,
            String homeTeamName,
            String awayTeamName,
            Integer ourSide,
            String ourSideLabel,
            Integer status,
            String statusLabel,
            boolean manualStatus,
            Integer capacity,
            Integer guestCapacity,
            Integer waitlistCapacity,
            boolean includeGuests,
            Integer scoreHome,
            Integer scoreAway,
            Integer seasonMatchNo,
            Integer totalMatchNo,
            int confirmedCount,
            int guestCount,
            int waitlistCount,
            Integer version,
            Instant statusUpdatedAt,
            Long statusUpdatedBy,
            Long createdBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record MatchSummary(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            Instant startAt,
            Instant endAt,
            String venue,
            Integer round,
            String season,
            String homeTeamName,
            String awayTeamName,
            Integer ourSide,
            String ourSideLabel,
            Integer scoreHome,
            Integer scoreAway,
            String resultText,
            Integer status,
            String statusLabel,
            Integer totalMatchNo,
            Integer seasonMatchNo,
            int confirmedCount,
            int guestCount,
            int waitlistCount) {
    }

    public record MatchDetail(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            Instant startAt,
            Instant endAt,
            String venue,
            Integer round,
            String season,
            String homeTeamName,
            String awayTeamName,
            Integer ourSide,
            String ourSideLabel,
            Integer scoreHome,
            Integer scoreAway,
            String resultText,
            Integer status,
            String statusLabel,
            Integer totalMatchNo,
            Integer seasonMatchNo,
            int confirmedCount,
            int guestCount,
            int waitlistCount,
            String format,
            String competition,
            Instant signupDeadline,
            Instant cancelLock,
            Integer capacity,
            Integer guestCapacity,
            Integer waitlistCapacity,
            Integer version,
            DerivedScore derivedScore,
            RegistrationView myRegistration) {
    }

    public record DerivedScore(Integer scoreHome, Integer scoreAway) {
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
}
