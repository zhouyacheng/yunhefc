package com.yunhefc.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.api.RequestContext;
import com.yunhefc.infra.outbox.OutboxService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditRecorder {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public AuditRecorder(OutboxService outboxService, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    public void record(Long actorUserId, String action, String targetType, Long targetId,
                       Object before, Object after) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actorUserId", actorUserId);
        payload.put("action", action);
        payload.put("targetType", targetType);
        payload.put("targetId", targetId);
        payload.put("beforeJson", json(before));
        payload.put("afterJson", json(after));
        payload.put("requestId", RequestContext.requestId());
        payload.put("ip", RequestContext.clientIp());
        outboxService.append(null, "AUDIT", targetId, "AUDIT_RECORDED", payload);
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
