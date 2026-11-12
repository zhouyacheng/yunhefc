package com.yunhefc.settings.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.settings.domain.SystemSetting;
import com.yunhefc.settings.dto.SettingsDtos.SettingsPatch;
import com.yunhefc.settings.dto.SettingsDtos.SettingsView;
import com.yunhefc.settings.infrastructure.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(SettingsApplicationService.class);

    private static final String MAX_PLAYERS_KEY = "registration.maxPlayers";
    private static final String MAX_GUESTS_KEY = "registration.maxGuests";
    private static final String MAX_WAITLIST_KEY = "registration.maxWaitlist";
    private static final String MANUAL_KEY = "grouping.manualWeight";
    private static final String AUTO_KEY = "grouping.autoWeight";
    private static final String APPEARANCE_KEY = "grouping.appearanceWeight";
    private static final String GOAL_KEY = "grouping.goalWeight";
    private static final String ASSIST_KEY = "grouping.assistWeight";
    private static final String MIN_APPEARANCES_KEY = "grouping.minAppearances";

    private static final List<String> KEYS = List.of(
            MAX_PLAYERS_KEY, MAX_GUESTS_KEY, MAX_WAITLIST_KEY,
            MANUAL_KEY, AUTO_KEY, APPEARANCE_KEY, GOAL_KEY, ASSIST_KEY, MIN_APPEARANCES_KEY);

    private static final int DEFAULT_MAX_PLAYERS = 30;
    private static final int DEFAULT_MAX_GUESTS = 4;
    private static final int DEFAULT_MAX_WAITLIST = 2;
    private static final int DEFAULT_MANUAL = 70;
    private static final int DEFAULT_AUTO = 30;
    private static final int DEFAULT_APPEARANCE = 50;
    private static final int DEFAULT_GOAL = 25;
    private static final int DEFAULT_ASSIST = 25;
    private static final int DEFAULT_MIN_APPEARANCES = 3;

    private final SystemSettingMapper systemSettingMapper;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SettingsView get() {
        return load(CurrentUser.principal().teamId());
    }

    @Transactional
    public SettingsView update(SettingsPatch patch) {
        AuthPrincipal actor = CurrentUser.principal();
        SettingsView before = load(actor.teamId());
        SettingsView after = merge(before, patch);
        validate(after);
        systemSettingMapper.upsertBatch(actor.teamId(), toValues(after), actor.userId(),
                Instant.now(clock));
        // 审计与配置写入同一事务；before/after 均为合并后的完整 9 键。
        auditRecorder.recordCurrent("SETTINGS_UPDATE", "SETTINGS", actor.teamId(), before, after);
        return after;
    }

    private SettingsView load(Long teamId) {
        List<SystemSetting> settings = systemSettingMapper.selectList(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getTeamId, teamId)
                .in(SystemSetting::getSettingKey, KEYS));
        Map<String, String> values = settings.stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue,
                        (first, second) -> first));
        return new SettingsView(
                integer(MAX_PLAYERS_KEY, values.get(MAX_PLAYERS_KEY), DEFAULT_MAX_PLAYERS),
                integer(MAX_GUESTS_KEY, values.get(MAX_GUESTS_KEY), DEFAULT_MAX_GUESTS),
                integer(MAX_WAITLIST_KEY, values.get(MAX_WAITLIST_KEY), DEFAULT_MAX_WAITLIST),
                integer(MANUAL_KEY, values.get(MANUAL_KEY), DEFAULT_MANUAL),
                integer(AUTO_KEY, values.get(AUTO_KEY), DEFAULT_AUTO),
                integer(APPEARANCE_KEY, values.get(APPEARANCE_KEY), DEFAULT_APPEARANCE),
                integer(GOAL_KEY, values.get(GOAL_KEY), DEFAULT_GOAL),
                integer(ASSIST_KEY, values.get(ASSIST_KEY), DEFAULT_ASSIST),
                integer(MIN_APPEARANCES_KEY, values.get(MIN_APPEARANCES_KEY), DEFAULT_MIN_APPEARANCES));
    }

    private int integer(String key, String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isIntegralNumber()) {
                log.debug("系统配置键不是整数，使用默认值, key={}", key);
                return fallback;
            }
            return node.intValue();
        } catch (Exception ex) {
            log.debug("系统配置键无法解析，使用默认值, key={}", key, ex);
            return fallback;
        }
    }

    private SettingsView merge(SettingsView before, SettingsPatch patch) {
        return new SettingsView(
                patch.maxPlayers() == null ? before.maxPlayers() : patch.maxPlayers(),
                patch.maxGuests() == null ? before.maxGuests() : patch.maxGuests(),
                patch.maxWaitlist() == null ? before.maxWaitlist() : patch.maxWaitlist(),
                patch.groupingManualWeight() == null
                        ? before.groupingManualWeight() : patch.groupingManualWeight(),
                patch.groupingAutoWeight() == null
                        ? before.groupingAutoWeight() : patch.groupingAutoWeight(),
                patch.groupingAppearanceWeight() == null
                        ? before.groupingAppearanceWeight() : patch.groupingAppearanceWeight(),
                patch.groupingGoalWeight() == null
                        ? before.groupingGoalWeight() : patch.groupingGoalWeight(),
                patch.groupingAssistWeight() == null
                        ? before.groupingAssistWeight() : patch.groupingAssistWeight(),
                patch.groupingMinAppearances() == null
                        ? before.groupingMinAppearances() : patch.groupingMinAppearances());
    }

    private void validate(SettingsView settings) {
        requireRange(settings.maxPlayers(), 1, 99, "报名默认人数");
        requireRange(settings.maxGuests(), 0, 99, "外挂默认上限");
        requireRange(settings.maxWaitlist(), 0, 99, "候补默认上限");
        requireRange(settings.groupingMinAppearances(), 0, 99, "最少出场次数");
        requireRange(settings.groupingManualWeight(), 0, 100, "手动权重");
        requireRange(settings.groupingAutoWeight(), 0, 100, "自动权重");
        requireRange(settings.groupingAppearanceWeight(), 0, 100, "出场权重");
        requireRange(settings.groupingGoalWeight(), 0, 100, "进球权重");
        requireRange(settings.groupingAssistWeight(), 0, 100, "助攻权重");

        // 与 GroupingWeights.resolve 同口径：主权重合计必须为 100，子权重合计必须大于 0。
        if (settings.groupingManualWeight() + settings.groupingAutoWeight() != 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "手动与自动权重合计必须为 100");
        }
        if (settings.groupingAppearanceWeight() + settings.groupingGoalWeight()
                + settings.groupingAssistWeight() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "出场、进球、助攻权重合计必须大于 0");
        }
    }

    private void requireRange(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    label + "必须在 " + min + "-" + max + " 之间");
        }
    }

    private Map<String, Integer> toValues(SettingsView settings) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put(MAX_PLAYERS_KEY, settings.maxPlayers());
        values.put(MAX_GUESTS_KEY, settings.maxGuests());
        values.put(MAX_WAITLIST_KEY, settings.maxWaitlist());
        values.put(MANUAL_KEY, settings.groupingManualWeight());
        values.put(AUTO_KEY, settings.groupingAutoWeight());
        values.put(APPEARANCE_KEY, settings.groupingAppearanceWeight());
        values.put(GOAL_KEY, settings.groupingGoalWeight());
        values.put(ASSIST_KEY, settings.groupingAssistWeight());
        values.put(MIN_APPEARANCES_KEY, settings.groupingMinAppearances());
        return values;
    }
}
