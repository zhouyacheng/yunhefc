package com.yunhefc.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.infra.outbox.ConsumedEventMapper;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.domain.Notification;
import com.yunhefc.notification.domain.NotificationType;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationEventHandler {

    public static final String GROUP = "yunhefc-notification";

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter START_AT_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final NotificationMapper notificationMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final RegistrationReadPort registrationReadPort;
    private final ObjectMapper objectMapper;

    @Transactional
    public void apply(String message, String topic, int partition, long offset) {
        try {
            OutboxMessage event = objectMapper.readValue(message, OutboxMessage.class);
            if (event.eventType() == null
                    || !EventTypes.NOTIFICATION_EVENTS.contains(event.eventType())) {
                return;
            }
            if (consumedEventMapper.tryInsert(GROUP, event.eventId(), topic, partition, offset) == 0) {
                return;
            }
            Map<String, Object> payload = objectMapper.readValue(event.payload(), new TypeReference<>() {
            });
            if (EventTypes.MATCH_POSTPONED.equals(event.eventType())
                    || EventTypes.MATCH_CANCELLED.equals(event.eventType())) {
                insertForActiveRegistrants(event, payload);
            } else {
                notificationMapper.insert(build(event.eventType(), payload));
            }
        } catch (Exception ex) {
            log.error("notification event handling failed, topic={}, partition={}, offset={}",
                    topic, partition, offset, ex);
            throw new IllegalStateException("处理通知事件失败", ex);
        }
    }

    /**
     * 赛程变更广播给比赛当前全部有效报名者；报名域只经 contracts 只读端口访问。
     */
    private void insertForActiveRegistrants(OutboxMessage event, Map<String, Object> payload) {
        Long matchId = longValue(payload.get("matchId"));
        List<Notification> notifications = registrationReadPort
                .findActiveUserIds(event.teamId(), matchId).stream()
                .map(userId -> buildMatchNotification(event.eventType(), payload, userId))
                .toList();
        if (!notifications.isEmpty()) {
            notificationMapper.insertBatch(notifications);
        }
    }

    private Notification buildMatchNotification(String eventType, Map<String, Object> payload,
                                                Long userId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setRefType("MATCH");
        notification.setRefId(longValue(payload.get("matchId")));
        String matchTitle = String.valueOf(payload.get("matchTitle"));
        if (EventTypes.MATCH_CANCELLED.equals(eventType)) {
            notification.setType(NotificationType.MATCH_CANCELLED);
            notification.setTitle("比赛取消");
            notification.setBody("「" + matchTitle + "」已取消：" + payload.getOrDefault("reason", ""));
            return notification;
        }
        boolean rescheduled = Boolean.TRUE.equals(payload.get("rescheduled"));
        notification.setType(NotificationType.MATCH_POSTPONED);
        notification.setTitle(rescheduled ? "比赛恢复报名" : "比赛延期");
        notification.setBody("「" + matchTitle + "」已" + (rescheduled ? "重新排期" : "延期")
                + "至 " + formatStartAt(payload.get("startAt")) + "，请留意最新安排。");
        return notification;
    }

    private static String formatStartAt(Object value) {
        return START_AT_FORMAT.format(Instant.parse(String.valueOf(value)).atZone(ZONE));
    }

    private Notification build(String eventType, Map<String, Object> payload) {
        Notification notification = new Notification();
        Long userId = longValue(payload.get("userId"));
        notification.setUserId(userId);
        switch (eventType) {
            case EventTypes.REGISTRATION_CONFIRMED -> {
                notification.setType(NotificationType.REGISTRATION_CONFIRMED);
                notification.setTitle("报名成功");
                notification.setBody("你已成功报名「" + payload.get("matchTitle") + "」，请留意赛前通知。");
                notification.setRefType("MATCH");
                notification.setRefId(longValue(payload.get("matchId")));
            }
            case EventTypes.WAITLIST_PROMOTED -> {
                notification.setType(NotificationType.WAITLIST_PROMOTED);
                notification.setTitle("候补递补成功");
                notification.setBody("你已递补为「" + payload.get("matchTitle") + "」的正式报名，请留意赛前通知。");
                notification.setRefType("MATCH");
                notification.setRefId(longValue(payload.get("matchId")));
            }
            case EventTypes.ACCOUNT_APPROVED -> {
                notification.setType(NotificationType.ACCOUNT_APPROVED);
                notification.setTitle("账号审核通过");
                notification.setBody("你的账号已审核通过，现在可以报名参加比赛");
                notification.setRefType("USER");
                notification.setRefId(userId);
            }
            case EventTypes.ACCOUNT_DISABLED -> {
                notification.setType(NotificationType.ACCOUNT_DISABLED);
                notification.setTitle("账号已注销");
                notification.setBody("你的账号已被管理员注销，历史数据将保留");
                notification.setRefType("USER");
                notification.setRefId(userId);
            }
            case EventTypes.ROLE_CHANGED -> {
                notification.setType(NotificationType.ROLE_CHANGED);
                notification.setTitle("角色变更");
                notification.setBody("你的角色已更新：" + payload.getOrDefault("roles", ""));
                notification.setRefType("USER");
                notification.setRefId(userId);
            }
            default -> throw new IllegalArgumentException("未知通知事件类型: " + eventType);
        }
        return notification;
    }

    private static Long longValue(Object value) {
        return Long.valueOf(String.valueOf(value));
    }
}
