package com.yunhefc.ledger.application;

import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.ledger.domain.LedgerCategory;
import com.yunhefc.ledger.domain.LedgerEntry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计 Outbox 快照；只保留业务字段，不含团队内部列。
 */
final class LedgerAuditSnapshot {

    private LedgerAuditSnapshot() {
    }

    static Map<String, Object> of(LedgerEntry entry) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", entry.getType() == null ? null : entry.getType().code());
        snapshot.put("categoryId", entry.getCategoryId());
        snapshot.put("amount", entry.getAmount());
        snapshot.put("description", entry.getDescription());
        snapshot.put("occurredAt", entry.getOccurredAt() == null
                ? null : ShanghaiTime.formatIso(entry.getOccurredAt()));
        snapshot.put("playerId", entry.getPlayerId());
        snapshot.put("activityId", entry.getActivityId());
        snapshot.put("version", entry.getVersion());
        return snapshot;
    }

    static Map<String, Object> of(LedgerCategory category) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", category.getName());
        snapshot.put("direction", category.getDirection() == null ? null : category.getDirection().code());
        snapshot.put("sortNo", category.getSortNo());
        snapshot.put("isActive", category.getIsActive());
        return snapshot;
    }
}
