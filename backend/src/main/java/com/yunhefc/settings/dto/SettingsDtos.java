package com.yunhefc.settings.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SettingsView(
            int maxPlayers,
            int maxGuests,
            int maxWaitlist) {
    }

    /**
     * 部分更新的任意子集；null 表示未传、保留原值。
     */
    @JsonDeserialize(using = SettingsPatchDeserializer.class)
    public record SettingsPatch(
            Integer maxPlayers,
            Integer maxGuests,
            Integer maxWaitlist) {
    }

    public record SettingsUpdateRequest(@NotNull SettingsPatch settings) {
    }

    public record SettingsResponse(SettingsView settings) {
    }
}
