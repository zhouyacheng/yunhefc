package com.yunhefc.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.infra.outbox.ConsumedEventMapper;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.domain.Notification;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationEventConsumer {

    private static final String GROUP = "yunhefc-notification";
    private static final String TOPIC = "${yunhefc.kafka.auth-topic:yunhefc.auth.events}";

    private final NotificationMapper notificationMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final ObjectMapper objectMapper;

    public NotificationEventConsumer(NotificationMapper notificationMapper,
                                     ConsumedEventMapper consumedEventMapper,
                                     ObjectMapper objectMapper) {
        this.notificationMapper = notificationMapper;
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
            if (!"ROLE_CHANGED".equals(event.eventType())) {
                return;
            }
            if (consumedEventMapper.tryInsert(GROUP, event.eventId(), topic, partition, offset) == 0) {
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(event.payload(), new TypeReference<>() {
            });
            Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(9);
            notification.setTitle("角色变更");
            notification.setBody("你的角色已更新：" + payload.getOrDefault("roles", ""));
            notification.setRefType("USER");
            notification.setRefId(userId);
            notificationMapper.insert(notification);
        } catch (Exception ex) {
            throw new IllegalStateException("处理通知事件失败", ex);
        }
    }
}
