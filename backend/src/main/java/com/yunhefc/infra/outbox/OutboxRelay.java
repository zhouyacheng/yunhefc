package com.yunhefc.infra.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.config.MessagingProperties;
import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.contracts.messaging.EventTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventMapper outboxEventMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties messagingProperties;
    private final OutboxProperties outboxProperties;
    private final Clock clock;
    private final String owner = UUID.randomUUID().toString();

    public OutboxRelay(
            OutboxEventMapper outboxEventMapper,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MessagingProperties messagingProperties,
            OutboxProperties outboxProperties,
            Clock clock) {
        this.outboxEventMapper = outboxEventMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.messagingProperties = messagingProperties;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
    }

    public void relay() {
        if (!outboxProperties.isEnabled()) {
            return;
        }
        List<OutboxEvent> events = outboxEventMapper.findClaimable(BATCH_SIZE);
        for (OutboxEvent event : events) {
            if (outboxEventMapper.claim(event.getId(), owner, Instant.now(clock).plusSeconds(60)) == 0) {
                continue;
            }
            try {
                OutboxMessage message = new OutboxMessage(
                        event.getEventId(), event.getTeamId(), event.getAggregateType(),
                        event.getAggregateId(), event.getEventType(), event.getPayloadJson());
                kafkaTemplate.send(topicFor(event.getEventType()), String.valueOf(event.getAggregateId()),
                        objectMapper.writeValueAsString(message)).get(5, TimeUnit.SECONDS);
                outboxEventMapper.markSent(event.getId());
            } catch (Exception ex) {
                int attempts = event.getAttempts() == null ? 1 : event.getAttempts() + 1;
                long delay = Math.min(300, 30L * attempts);
                outboxEventMapper.markFailed(event.getId(), Instant.now(clock).plusSeconds(delay),
                        truncate(ex.getMessage()), MAX_ATTEMPTS);
                log.warn("Outbox event failed, eventId={}, attempts={}",
                        event.getEventId(), attempts, ex);
            }
        }
    }

    private String topicFor(String eventType) {
        if (EventTypes.AUDIT_RECORDED.equals(eventType)) {
            return messagingProperties.getAuditTopic();
        }
        if (EventTypes.ROLE_CHANGED.equals(eventType)) {
            return messagingProperties.getNotificationTopic();
        }
        throw new IllegalArgumentException("事件类型未配置 topic: " + eventType);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
