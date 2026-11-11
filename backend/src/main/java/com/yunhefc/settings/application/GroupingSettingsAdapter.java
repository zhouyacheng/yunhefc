package com.yunhefc.settings.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.settings.GroupingSettings;
import com.yunhefc.contracts.settings.GroupingSettingsPort;
import com.yunhefc.settings.domain.SystemSetting;
import com.yunhefc.settings.infrastructure.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 读取分组评分配置；缺失或不可解析的键逐项回退默认值，范围合法性由分组领域层裁决。
 */
@Component
@RequiredArgsConstructor
public class GroupingSettingsAdapter implements GroupingSettingsPort {

    private static final String MANUAL_KEY = "grouping.manualWeight";
    private static final String AUTO_KEY = "grouping.autoWeight";
    private static final String APPEARANCE_KEY = "grouping.appearanceWeight";
    private static final String GOAL_KEY = "grouping.goalWeight";
    private static final String ASSIST_KEY = "grouping.assistWeight";
    private static final String MIN_APPEARANCES_KEY = "grouping.minAppearances";

    private static final BigDecimal DEFAULT_MANUAL = BigDecimal.valueOf(70);
    private static final BigDecimal DEFAULT_AUTO = BigDecimal.valueOf(30);
    private static final BigDecimal DEFAULT_APPEARANCE = BigDecimal.valueOf(50);
    private static final BigDecimal DEFAULT_GOAL = BigDecimal.valueOf(25);
    private static final BigDecimal DEFAULT_ASSIST = BigDecimal.valueOf(25);
    private static final int DEFAULT_MIN_APPEARANCES = 3;

    private final SystemSettingMapper systemSettingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public GroupingSettings load(Long teamId) {
        List<SystemSetting> settings = systemSettingMapper.selectList(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getTeamId, teamId)
                .in(SystemSetting::getSettingKey, List.of(MANUAL_KEY, AUTO_KEY, APPEARANCE_KEY,
                        GOAL_KEY, ASSIST_KEY, MIN_APPEARANCES_KEY)));
        Map<String, String> values = settings.stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue,
                        (first, second) -> first));
        return new GroupingSettings(
                decimal(values.get(MANUAL_KEY), DEFAULT_MANUAL),
                decimal(values.get(AUTO_KEY), DEFAULT_AUTO),
                decimal(values.get(APPEARANCE_KEY), DEFAULT_APPEARANCE),
                decimal(values.get(GOAL_KEY), DEFAULT_GOAL),
                decimal(values.get(ASSIST_KEY), DEFAULT_ASSIST),
                integer(values.get(MIN_APPEARANCES_KEY), DEFAULT_MIN_APPEARANCES));
    }

    private BigDecimal decimal(String raw, BigDecimal fallback) {
        JsonNode node = parse(raw);
        return node != null && node.isNumber() ? node.decimalValue() : fallback;
    }

    private int integer(String raw, int fallback) {
        JsonNode node = parse(raw);
        return node != null && node.isIntegralNumber() ? node.intValue() : fallback;
    }

    private JsonNode parse(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
