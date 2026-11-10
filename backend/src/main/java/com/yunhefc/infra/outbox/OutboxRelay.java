package com.yunhefc.infra.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.config.MessagingProperties;
import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.contracts.messaging.EventTypes;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
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

    public void relay() {
        if (!outboxProperties.isEnabled()) {
            return;
        }
        List<OutboxEvent> events = outboxEventMapper.findClaimable(BATCH_SIZE);
        List<PendingSend> pending = new ArrayList<>();
        for (OutboxEvent event : events) {
            if (outboxEventMapper.claim(event.getId(), owner, Instant.now(clock).plusSeconds(60)) == 0) {
                continue;
            }
            try {
                OutboxMessage message = new OutboxMessage(
                        event.getEventId(), event.getTeamId(), event.getAggregateType(),
                        event.getAggregateId(), event.getEventType(), event.getPayloadJson());
                // 先全部发送再统一等待，避免逐条 send().get() 串行阻塞
                pending.add(new PendingSend(event, kafkaTemplate.send(topicFor(event.getEventType()),
                        String.valueOf(event.getAggregateId()),
                        objectMapper.writeValueAsString(message))));
            } catch (Exception ex) {
                markFailed(event, ex);
            }
        }
        for (PendingSend send : pending) {
            try {
                send.future().get(5, TimeUnit.SECONDS);
                outboxEventMapper.markSent(send.event().getId());
            } catch (Exception ex) {
                markFailed(send.event(), ex);
            }
        }
    }

    private void markFailed(OutboxEvent event, Exception ex) {
        int attempts = event.getAttempts() == null ? 1 : event.getAttempts() + 1;
        long delay = Math.min(300, 30L * attempts);
        outboxEventMapper.markFailed(event.getId(), Instant.now(clock).plusSeconds(delay),
                truncate(ex.getMessage()), MAX_ATTEMPTS);
        log.warn("Outbox event failed, eventId={}, attempts={}", event.getEventId(), attempts, ex);
    }

    private record PendingSend(OutboxEvent event, CompletableFuture<SendResult<String, String>> future) {
    }

    private String topicFor(String eventType) {
        if (EventTypes.AUDIT_RECORDED.equals(eventType)) {
            return messagingProperties.getAuditTopic();
        }
        if (EventTypes.ROLE_CHANGED.equals(eventType)
                || EventTypes.ACCOUNT_APPROVED.equals(eventType)) {
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
