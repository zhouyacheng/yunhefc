package com.yunhefc.lineup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
}
