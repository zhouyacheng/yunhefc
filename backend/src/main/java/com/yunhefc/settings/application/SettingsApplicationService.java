package com.yunhefc.settings.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.time.ShanghaiTime;
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

import java.time.LocalDateTime;
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

    private static final List<String> KEYS = List.of(
            MAX_PLAYERS_KEY, MAX_GUESTS_KEY, MAX_WAITLIST_KEY);

    private static final int DEFAULT_MAX_PLAYERS = 30;
    private static final int DEFAULT_MAX_GUESTS = 4;
    private static final int DEFAULT_MAX_WAITLIST = 2;

    private final SystemSettingMapper systemSettingMapper;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;

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
                ShanghaiTime.now());
        // 审计与配置写入同一事务；before/after 均为合并后的完整 3 键。
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
                integer(MAX_WAITLIST_KEY, values.get(MAX_WAITLIST_KEY), DEFAULT_MAX_WAITLIST));
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
                patch.maxWaitlist() == null ? before.maxWaitlist() : patch.maxWaitlist());
    }

    private void validate(SettingsView settings) {
        requireRange(settings.maxPlayers(), 1, 99, "报名默认人数");
        requireRange(settings.maxGuests(), 0, 99, "外挂默认上限");
        requireRange(settings.maxWaitlist(), 0, 99, "候补默认上限");
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
        return values;
    }
}
