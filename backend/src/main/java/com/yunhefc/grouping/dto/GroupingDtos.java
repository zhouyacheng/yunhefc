package com.yunhefc.grouping.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class GroupingDtos {

    private GroupingDtos() {
    }

    public record GroupedPlayerView(
            Long playerId,
            String name,
            Integer jersey,
            Integer position,
            BigDecimal manualScore,
            BigDecimal autoScore,
            BigDecimal finalScore) {
    }

    public record GroupingTeamsView(
            @JsonProperty("A") List<GroupedPlayerView> teamA,
            @JsonProperty("B") List<GroupedPlayerView> teamB) {
    }

    public record GroupingWeightsView(
            BigDecimal manualWeight,
            BigDecimal autoWeight,
            BigDecimal appearanceWeight,
            BigDecimal goalWeight,
            BigDecimal assistWeight,
            int minAppearances) {
    }

    public record GroupingView(
            GroupingTeamsView teams,
            GroupingWeightsView weights,
            BigDecimal diff,
            List<String> warnings,
            Instant savedAt) {
    }

    public record GroupingScoresView(
            @JsonProperty("A") BigDecimal teamA,
            @JsonProperty("B") BigDecimal teamB,
            BigDecimal diff) {
    }

    public record GroupingRosterDiffView(
            boolean changed,
            List<Long> addedPlayerIds,
            List<Long> removedPlayerIds) {
    }

    public record GroupingAdminView(
            GroupingTeamsView teams,
            GroupingWeightsView weights,
            BigDecimal diff,
            List<String> warnings,
            Instant savedAt,
            GroupingScoresView scores,
            String rosterFingerprint,
            Instant generatedAt,
            List<Long> ungroupedPlayerIds,
            GroupingRosterDiffView rosterDiff) {
    }

    public record GroupingGenerateRequest(Boolean confirm) {
    }

    public record GroupingTeamsRequest(
            @JsonProperty("A") @NotNull @Size(max = 100) List<@NotNull Long> teamA,
            @JsonProperty("B") @NotNull @Size(max = 100) List<@NotNull Long> teamB) {
    }

    public record GroupingAdjustRequest(
            @Valid @NotNull GroupingTeamsRequest teams,
            @NotBlank @Size(max = 64) String expectedFingerprint,
            Boolean confirm) {
    }

    public record GroupingSaveRequest(
            @NotBlank @Size(max = 64) String expectedFingerprint,
            Boolean confirm) {
    }

    public record GroupingSnapshotView(
            Instant savedAt,
            String rosterFingerprint,
            GroupingTeamsView teams) {
    }

    public record GroupingSaveResponse(
            Instant savedAt,
            GroupingSnapshotView snapshot) {
    }

    public record GroupingClearResponse(boolean cleared) {
    }

    public record GroupingResponse(GroupingAdminView grouping) {
    }
}
