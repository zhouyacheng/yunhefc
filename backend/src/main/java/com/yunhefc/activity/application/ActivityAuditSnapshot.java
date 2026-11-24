package com.yunhefc.activity.application;

import com.yunhefc.activity.domain.TeamActivity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 活动审计快照：创建、编辑与状态变更共用同一字段集合与顺序。
 */
final class ActivityAuditSnapshot {

    private ActivityAuditSnapshot() {
    }

    static Map<String, Object> of(TeamActivity activity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", activity.getTitle());
        values.put("type", activity.getType().code());
        values.put("startAt", activity.getStartAt());
        values.put("endAt", activity.getEndAt());
        values.put("signupDeadline", activity.getSignupDeadline());
        values.put("capacity", activity.getCapacity());
        values.put("costPerPerson", activity.getCostPerPerson());
        values.put("location", activity.getLocation());
        values.put("locationImageKey", activity.getLocationImageKey());
        values.put("description", activity.getDescription());
        values.put("status", activity.getStatus().code());
        values.put("cancelReason", activity.getCancelReason());
        return values;
    }
}
