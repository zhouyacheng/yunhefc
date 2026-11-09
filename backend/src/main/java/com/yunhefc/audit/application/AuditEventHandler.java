package com.yunhefc.audit.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.domain.AuditLog;
import com.yunhefc.audit.infrastructure.AuditLogMapper;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.ConsumedEventMapper;
import com.yunhefc.infra.outbox.OutboxMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditEventHandler {

    public static final String GROUP = "yunhefc-audit";

    private final AuditLogMapper auditLogMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final ObjectMapper objectMapper;

    public AuditEventHandler(AuditLogMapper auditLogMapper,
                             ConsumedEventMapper consumedEventMapper,
                             ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void apply(String message, String topic, int partition, long offset) {
        try {
            OutboxMessage event = objectMapper.readValue(message, OutboxMessage.class);
            if (!EventTypes.AUDIT_RECORDED.equals(event.eventType())) {
                return;
            }
            if (consumedEventMapper.tryInsert(GROUP, event.eventId(), topic, partition, offset) == 0) {
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(event.payload(), new TypeReference<>() {
            });
            AuditLog log = new AuditLog();
            log.setTeamId(asLong(payload.get("teamId")));
            log.setActorUserId(asLong(payload.get("actorUserId")));
            log.setActorName(asString(payload.get("actorName")));
            log.setAction(String.valueOf(payload.get("action")));
            log.setTargetType(String.valueOf(payload.get("targetType")));
            log.setTargetId(asLong(payload.get("targetId")));
            log.setBeforeJson(asString(payload.get("beforeJson")));
            log.setAfterJson(asString(payload.get("afterJson")));
            log.setRequestId(asString(payload.get("requestId")));
            log.setIp(asString(payload.get("ip")));
            auditLogMapper.insert(log);
        } catch (Exception ex) {
            throw new IllegalStateException("处理审计事件失败", ex);
        }
    }

    private Long asLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
