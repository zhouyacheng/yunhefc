package com.yunhefc.settings.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.settings.GroupingSettings;
import com.yunhefc.settings.domain.SystemSetting;
import com.yunhefc.settings.infrastructure.SystemSettingMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupingSettingsAdapterTest {

    private final SystemSettingMapper mapper = mock(SystemSettingMapper.class);
    private final GroupingSettingsAdapter adapter =
            new GroupingSettingsAdapter(mapper, new ObjectMapper());

    @Test
    void readsAllConfiguredValues() {
        when(mapper.selectList(any())).thenReturn(List.of(
                setting("grouping.manualWeight", "60"),
                setting("grouping.autoWeight", "40"),
                setting("grouping.appearanceWeight", "40"),
                setting("grouping.goalWeight", "40"),
                setting("grouping.assistWeight", "20"),
                setting("grouping.minAppearances", "5")));

        GroupingSettings settings = adapter.load(1L);

        assertThat(settings.manualWeight()).isEqualByComparingTo("60");
        assertThat(settings.autoWeight()).isEqualByComparingTo("40");
        assertThat(settings.appearanceWeight()).isEqualByComparingTo("40");
        assertThat(settings.goalWeight()).isEqualByComparingTo("40");
        assertThat(settings.assistWeight()).isEqualByComparingTo("20");
        assertThat(settings.minAppearances()).isEqualTo(5);
    }

    @Test
    void missingKeysFallBackPerKey() {
        when(mapper.selectList(any())).thenReturn(List.of());

        GroupingSettings settings = adapter.load(1L);

        assertThat(settings.manualWeight()).isEqualByComparingTo("70");
        assertThat(settings.autoWeight()).isEqualByComparingTo("30");
        assertThat(settings.appearanceWeight()).isEqualByComparingTo("50");
        assertThat(settings.goalWeight()).isEqualByComparingTo("25");
        assertThat(settings.assistWeight()).isEqualByComparingTo("25");
        assertThat(settings.minAppearances()).isEqualTo(3);
    }

    @Test
    void nonNumericValuesFallBackButNegativeValuesReachTheDomainLayer() {
        when(mapper.selectList(any())).thenReturn(List.of(
                setting("grouping.manualWeight", "not-a-number"),
                setting("grouping.autoWeight", "-5"),
                setting("grouping.minAppearances", "2.5")));

        GroupingSettings settings = adapter.load(1L);

        assertThat(settings.manualWeight()).isEqualByComparingTo("70");
        assertThat(settings.autoWeight()).isEqualByComparingTo("-5");
        assertThat(settings.minAppearances()).isEqualTo(3);
    }

    private static SystemSetting setting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
