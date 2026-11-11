package com.yunhefc.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class EventDtos {

    private EventDtos() {
    }

    public record EventCreateRequest(
            @NotNull @Min(0) @Max(4) Integer type,
            @NotNull @Min(0) @Max(1) Integer team,
            Long playerId,
            Long assistPlayerId,
            Long inPlayerId,
            Long outPlayerId,
            @NotNull @Min(1) @Max(120) Integer minute,
            @NotNull @Min(0) @Max(1) Integer half,
            Boolean penalty) {
    }

    /**
     * PATCH 语义：未出现的字段保持原值；显式 null 可清除球员/助攻/换上/换下。
     */
    @Getter
    @Setter
    public static class EventUpdateRequest {

        private Integer type;
        private Integer team;
        private Long playerId;
        private Long assistPlayerId;
        private Long inPlayerId;
        private Long outPlayerId;
        private Integer minute;
        private Integer half;
        private Boolean penalty;

        @NotNull
        @Min(0)
        private Integer version;

        @JsonIgnore
        private final Set<String> presentFields = new LinkedHashSet<>();

        @JsonProperty("playerId")
        public void setPlayerId(Long playerId) {
            presentFields.add("playerId");
            this.playerId = playerId;
        }

        @JsonProperty("assistPlayerId")
        public void setAssistPlayerId(Long assistPlayerId) {
            presentFields.add("assistPlayerId");
            this.assistPlayerId = assistPlayerId;
        }

        @JsonProperty("inPlayerId")
        public void setInPlayerId(Long inPlayerId) {
            presentFields.add("inPlayerId");
            this.inPlayerId = inPlayerId;
        }

        @JsonProperty("outPlayerId")
        public void setOutPlayerId(Long outPlayerId) {
            presentFields.add("outPlayerId");
            this.outPlayerId = outPlayerId;
        }

        @JsonProperty("penalty")
        public void setPenalty(Boolean penalty) {
            presentFields.add("penalty");
            this.penalty = penalty;
        }

        @JsonIgnore
        public boolean has(String field) {
            return presentFields.contains(field);
        }
    }

    public record EventDeleteRequest(@NotNull @Min(0) Integer version) {
    }

    public record ScoreUpdateRequest(
            @NotNull @Min(0) @Max(255) Integer scoreHome,
            @NotNull @Min(0) @Max(255) Integer scoreAway,
            @NotNull @Min(0) Integer version) {
    }

    public record EventPlayer(
            Long id,
            String name,
            Integer jersey,
            Integer position,
            String positionLabel) {
    }

    public record EventView(
            Long id,
            Integer type,
            String typeLabel,
            Integer team,
            String teamLabel,
            EventPlayer player,
            EventPlayer assistPlayer,
            EventPlayer inPlayer,
            EventPlayer outPlayer,
            Integer minute,
            Integer half,
            String halfLabel,
            boolean penalty,
            Long createdBy,
            Instant updatedAt,
            Integer version) {
    }

    public record DerivedScoreView(int scoreHome, int scoreAway) {
    }

    public record EventTimeline(List<EventView> items, DerivedScoreView derivedScore) {
    }

    public record EventResponse(EventView event) {
    }

    public record EventDeleteResponse(Long eventId, Instant deletedAt) {
    }

    /**
     * 存储比分与事件推导比分并排返回；两者不一致时仅提示，不阻断。
     */
    public record ScoreView(Integer scoreHome, Integer scoreAway, DerivedScoreView derived) {
    }

    public record ScoreResponse(ScoreView score) {
    }
}
