package com.yunhefc.audit.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.domain.AuditLog;
import com.yunhefc.audit.infrastructure.AuditLogMapper;
import com.yunhefc.infra.outbox.ConsumedEventMapper;
import com.yunhefc.infra.outbox.OutboxMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditEventConsumer {

    private static final String GROUP = "yunhefc-audit";
    private static final String TOPIC = "${yunhefc.kafka.auth-topic:yunhefc.auth.events}";

    private final AuditLogMapper auditLogMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditLogMapper auditLogMapper,
                              ConsumedEventMapper consumedEventMapper,
                              ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP,
            autoStartup = "${yunhefc.kafka.listener-enabled:true}")
    public void consume(String message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        apply(message, topic, partition, offset);
    }

    public void apply(String message, String topic, int partition, long offset) {
        try {
            OutboxMessage event = objectMapper.readValue(message, OutboxMessage.class);
            if (!"AUDIT_RECORDED".equals(event.eventType())) {
                return;
            }
            if (consumedEventMapper.tryInsert(GROUP, event.eventId(), topic, partition, offset) == 0) {
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(event.payload(), new TypeReference<>() {
            });
            AuditLog log = new AuditLog();
            log.setActorUserId(asLong(payload.get("actorUserId")));
            log.setAction(String.valueOf(payload.get("action")));
            log.setTargetType(String.valueOf(payload.get("targetType")));
            log.setTargetId(asLong(payload.get("targetId")));
            log.setBeforeJson((String) payload.get("beforeJson"));
            log.setAfterJson((String) payload.get("afterJson"));
            log.setRequestId((String) payload.get("requestId"));
            log.setIp((String) payload.get("ip"));
            auditLogMapper.insert(log);
        } catch (Exception ex) {
            throw new IllegalStateException("处理审计事件失败", ex);
        }
    }

    private Long asLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }
}
