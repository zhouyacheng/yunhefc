package com.yunhefc.settings.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsView(
            int maxPlayers,
            int maxGuests,
            int maxWaitlist,
            int groupingManualWeight,
            int groupingAutoWeight,
            int groupingAppearanceWeight,
            int groupingGoalWeight,
            int groupingAssistWeight,
            int groupingMinAppearances) {
    }

    /**
     * 部分更新的任意子集；null 表示未传、保留原值。
     */
    @JsonDeserialize(using = SettingsPatchDeserializer.class)
    public record SettingsPatch(
            Integer maxPlayers,
            Integer maxGuests,
            Integer maxWaitlist,
            Integer groupingManualWeight,
            Integer groupingAutoWeight,
            Integer groupingAppearanceWeight,
            Integer groupingGoalWeight,
            Integer groupingAssistWeight,
            Integer groupingMinAppearances) {
    }

    public record SettingsUpdateRequest(@NotNull SettingsPatch settings) {
    }

    public record SettingsResponse(SettingsView settings) {
    }
}
