package com.yunhefc.registration.application;

import com.yunhefc.contracts.match.MatchSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报名域发往通知消费者的事件载荷；报名成功与递补共用同一字段约定。
 */
final class RegistrationNotificationPayload {

    private RegistrationNotificationPayload() {
    }

    static Map<String, Object> of(MatchSnapshot match, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("matchId", match.id());
        payload.put("matchTitle", match.title());
        payload.put("season", match.effectiveSeason());
        payload.put("seasonMatchNo", match.seasonMatchNo());
        payload.put("totalMatchNo", match.totalMatchNo());
        return payload;
    }
}
