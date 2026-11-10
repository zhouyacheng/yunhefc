package com.yunhefc.settings.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.settings.RegistrationDefaults;
import com.yunhefc.contracts.settings.RegistrationDefaultsPort;
import com.yunhefc.settings.domain.SystemSetting;
import com.yunhefc.settings.infrastructure.SystemSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RegistrationDefaultsAdapter implements RegistrationDefaultsPort {

    private static final String MAX_PLAYERS_KEY = "registration.maxPlayers";
    private static final String MAX_GUESTS_KEY = "registration.maxGuests";
    private static final String MAX_WAITLIST_KEY = "registration.maxWaitlist";

    private final SystemSettingMapper systemSettingMapper;
    private final ObjectMapper objectMapper;

    @Override
    public RegistrationDefaults load(Long teamId) {
        List<SystemSetting> settings = systemSettingMapper.selectList(new LambdaQueryWrapper<SystemSetting>()
                .eq(SystemSetting::getTeamId, teamId)
                .in(SystemSetting::getSettingKey, List.of(MAX_PLAYERS_KEY, MAX_GUESTS_KEY, MAX_WAITLIST_KEY)));
        Map<String, String> values = settings.stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue,
                        (first, second) -> first));
        int maxPlayers = parseSettingNumber(values.get(MAX_PLAYERS_KEY), 30, MAX_PLAYERS_KEY);
        int maxGuests = parseSettingNumber(values.get(MAX_GUESTS_KEY), 4, MAX_GUESTS_KEY);
        int maxWaitlist = parseSettingNumber(values.get(MAX_WAITLIST_KEY), 2, MAX_WAITLIST_KEY);
        if (maxPlayers < 1 || maxGuests < 0 || maxWaitlist < 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报名容量配置非法");
        }
        return new RegistrationDefaults(maxPlayers, maxGuests, maxWaitlist);
    }

    private int parseSettingNumber(String raw, int fallback, String key) {
        if (raw == null) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isIntegralNumber()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统配置格式错误: " + key);
            }
            return node.intValue();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "系统配置格式错误: " + key);
        }
    }
}
