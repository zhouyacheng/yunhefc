package com.yunhefc.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.ConsumedEventMapper;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.domain.Notification;
import com.yunhefc.notification.domain.NotificationType;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class NotificationEventHandler {

    public static final String GROUP = "yunhefc-notification";

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final NotificationMapper notificationMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final ObjectMapper objectMapper;

    public NotificationEventHandler(NotificationMapper notificationMapper,
                                    ConsumedEventMapper consumedEventMapper,
                                    ObjectMapper objectMapper) {
        this.notificationMapper = notificationMapper;
        this.consumedEventMapper = consumedEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void apply(String message, String topic, int partition, long offset) {
        try {
            OutboxMessage event = objectMapper.readValue(message, OutboxMessage.class);
            if (!EventTypes.ROLE_CHANGED.equals(event.eventType())) {
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
            notification.setType(NotificationType.ROLE_CHANGED);
            notification.setTitle("角色变更");
            notification.setBody("你的角色已更新：" + payload.getOrDefault("roles", ""));
            notification.setRefType("USER");
            notification.setRefId(userId);
            notificationMapper.insert(notification);
        } catch (Exception ex) {
            log.error("notification event handling failed, topic={}, partition={}, offset={}",
                    topic, partition, offset, ex);
            throw new IllegalStateException("处理通知事件失败", ex);
        }
    }
}
