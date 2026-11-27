package com.yunhefc.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.messaging.AuditEventSink;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.messaging.NotificationEventSink;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 进程内 Outbox 事件分发器：按事件类型路由到审计或通知处理器。
 * 返回 Future 以把异步异常传回 OutboxRelay，触发 outbox 重试与 FAILED 终态。
 */
@Component
@RequiredArgsConstructor
public class OutboxEventDispatcher {

    /** 写入 consumed_event.topic 的通道标识，与历史 Kafka topic 隔离，避免位点键冲突。 */
    public static final String AUDIT_CHANNEL = "outbox.audit";
    public static final String NOTIFICATION_CHANNEL = "outbox.notification";
    public static final int CHANNEL_PARTITION = 0;

    private final AuditEventSink auditEventSink;
    private final NotificationEventSink notificationEventSink;
    private final ObjectMapper objectMapper;

    @Async(OutboxDispatcherConfig.EXECUTOR)
    public CompletableFuture<Void> dispatch(OutboxEvent event) {
        String message = toMessageJson(event);
        String eventType = event.getEventType();
        if (EventTypes.AUDIT_RECORDED.equals(eventType)) {
            auditEventSink.apply(message, AUDIT_CHANNEL, CHANNEL_PARTITION, event.getId());
        } else if (eventType != null && EventTypes.NOTIFICATION_EVENTS.contains(eventType)) {
            notificationEventSink.apply(message, NOTIFICATION_CHANNEL, CHANNEL_PARTITION, event.getId());
        } else {
            throw new IllegalArgumentException("事件类型无对应投递通道: " + eventType);
        }
        return CompletableFuture.completedFuture(null);
    }

    private String toMessageJson(OutboxEvent event) {
        try {
            return objectMapper.writeValueAsString(new OutboxMessage(
                    event.getEventId(), event.getTeamId(), event.getAggregateType(),
                    event.getAggregateId(), event.getEventType(), event.getPayloadJson()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 Outbox 事件", ex);
        }
    }
}
