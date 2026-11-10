package com.yunhefc.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.api.RequestContext;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public void recordCurrent(String action, String targetType, Long targetId,
                              Object before, Object after) {
        AuthPrincipal actor = CurrentUser.principal();
        record(actor.teamId(), actor.userId(), actor.name(), action, targetType, targetId, before, after);
    }

    public void record(Long teamId, Long actorUserId, String actorName, String action,
                       String targetType, Long targetId, Object before, Object after) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("teamId", teamId);
        payload.put("actorUserId", actorUserId);
        payload.put("actorName", actorName);
        payload.put("action", action);
        payload.put("targetType", targetType);
        payload.put("targetId", targetId);
        payload.put("beforeJson", json(before));
        payload.put("afterJson", json(after));
        payload.put("requestId", RequestContext.requestId());
        payload.put("ip", RequestContext.clientIp());
        outboxService.append(teamId, "AUDIT", targetId, EventTypes.AUDIT_RECORDED, payload);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化审计快照", ex);
        }
    }
}
