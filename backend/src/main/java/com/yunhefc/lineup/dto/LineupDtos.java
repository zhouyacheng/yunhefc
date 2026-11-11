package com.yunhefc.lineup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LineupDtos {

    private LineupDtos() {
    }

    public record FormationSlotView(
            String slotKey,
            int role,
            String roleLabel,
            BigDecimal x,
            BigDecimal y) {
    }

    public record FormationTemplateView(
            Long id,
            String name,
            boolean isPreset,
            Long baseTemplateId,
            List<FormationSlotView> slots) {
    }

    public record FormationTemplateListResponse(List<FormationTemplateView> items) {
    }

    public record FormationTemplateResponse(FormationTemplateView template) {
    }

    public record FormationTemplateCreateRequest(
            @NotNull Long baseTemplateId,
            @NotBlank @Size(max = 64) String name) {
    }

    public record FormationTemplateRenameRequest(@NotBlank @Size(max = 64) String name) {
    }

    public record LineupPlayerView(
            Long id,
            String name,
            Integer jersey,
            Integer position,
            String positionLabel) {
    }

    public record LineupStarterView(String slotKey, LineupPlayerView player) {
    }

    /** 05 §4 LineupView；未保存时 formation 为默认阵型、starters 为空槽、bench 为全部可排球员。 */
    public record LineupView(
            int side,
            FormationTemplateView formation,
            List<LineupStarterView> starters,
            List<LineupPlayerView> bench) {
    }

    public record LineupAdminView(
            int side,
            FormationTemplateView formation,
            List<LineupStarterView> starters,
            List<LineupPlayerView> bench,
            int version,
            Instant updatedAt,
            Long updatedBy) {
    }

    public record LineupSlotRequest(
            @NotBlank @Size(max = 16) String slotKey,
            @Positive Long playerId) {
    }

    /** 05 §3.8 LineupSave；我方首发 8 人且 playerId 必填，对手方允许空槽位。 */
    public record LineupSaveRequest(
            @NotNull @Positive Long formationTemplateId,
            @NotNull @Size(max = 32) @Valid List<LineupSlotRequest> starters,
            @NotNull @Size(max = 100) List<@NotNull @Positive Long> bench,
            @NotNull @Min(0) Integer version) {
    }

    public record LineupSaveResponse(int version) {
    }

    public record LineupTemplateRequest(@NotBlank @Size(max = 64) String name) {
    }

    public record LineupTemplateResponse(Long templateId) {
    }
}
