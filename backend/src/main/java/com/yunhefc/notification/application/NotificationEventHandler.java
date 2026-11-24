package com.yunhefc.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.contracts.activity.ActivitySignupReadPort;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.security.UserAccountReadPort;
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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationEventHandler {

    public static final String GROUP = "yunhefc-notification";

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);
    private static final DateTimeFormatter START_AT_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final int ACTIVE_USER = 1;

    private final NotificationMapper notificationMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final RegistrationReadPort registrationReadPort;
    private final ActivitySignupReadPort activitySignupReadPort;
    private final UserAccountReadPort userAccountReadPort;
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
            } else if (EventTypes.ACTIVITY_CANCELLED.equals(event.eventType())) {
                insertForActivitySignups(event, payload);
            } else if (EventTypes.ACTIVITY_CREATED.equals(event.eventType())) {
                insertForActiveAccounts(payload);
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

    /**
     * 活动创建广播全部正常账号；ref_type=ACTIVITY，点击进入活动详情。
     */
    private void insertForActiveAccounts(Map<String, Object> payload) {
        List<Notification> notifications = userAccountReadPort.findUserIds(ACTIVE_USER, null).stream()
                .map(userId -> buildActivityCreated(payload, userId))
                .toList();
        if (!notifications.isEmpty()) {
            notificationMapper.insertBatch(notifications);
        }
    }

    /**
     * 活动取消只通知仍有效的报名者；报名域经 contracts 只读端口访问。
     */
    private void insertForActivitySignups(OutboxMessage event, Map<String, Object> payload) {
        Long activityId = longValue(payload.get("activityId"));
        List<Notification> notifications = activitySignupReadPort
                .findActiveUserIds(event.teamId(), activityId).stream()
                .map(userId -> buildActivityCancelled(payload, userId))
                .toList();
        if (!notifications.isEmpty()) {
            notificationMapper.insertBatch(notifications);
        }
    }

    private Notification buildActivityCreated(Map<String, Object> payload, Long userId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(NotificationType.ACTIVITY_CREATED);
        notification.setTitle("新活动");
        notification.setBody("「" + payload.get("activityTitle") + "」将于 "
                + formatStartAt(payload.get("startAt")) + " 举行，地点："
                + payload.get("location") + "，欢迎报名。");
        notification.setRefType("ACTIVITY");
        notification.setRefId(longValue(payload.get("activityId")));
        return notification;
    }

    private Notification buildActivityCancelled(Map<String, Object> payload, Long userId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(NotificationType.ACTIVITY_CANCELLED);
        notification.setTitle("活动取消");
        notification.setBody("「" + payload.get("activityTitle") + "」已取消："
                + payload.getOrDefault("reason", ""));
        notification.setRefType("ACTIVITY");
        notification.setRefId(longValue(payload.get("activityId")));
        return notification;
    }

    private static String formatStartAt(Object value) {
        return START_AT_FORMAT.format(ShanghaiTime.parseLenient(String.valueOf(value)));
    }

    /**
     * 赛程场次前缀（末尾带空格），如“2026赛季第5场（累计5场） ”；旧载荷缺字段时省略，避免重放出残缺文案。
     */
    private static String seasonMatchText(Map<String, Object> payload) {
        Object season = payload.get("season");
        Object seasonMatchNo = payload.get("seasonMatchNo");
        Object totalMatchNo = payload.get("totalMatchNo");
        if (season == null || seasonMatchNo == null || totalMatchNo == null) {
            return "";
        }
        return season + "赛季第" + seasonMatchNo + "场（累计" + totalMatchNo + "场） ";
    }

    private Notification build(String eventType, Map<String, Object> payload) {
        Notification notification = new Notification();
        Long userId = longValue(payload.get("userId"));
        notification.setUserId(userId);
        switch (eventType) {
            case EventTypes.REGISTRATION_CONFIRMED -> {
                notification.setType(NotificationType.REGISTRATION_CONFIRMED);
                notification.setTitle("报名成功");
                notification.setBody("你已成功报名「" + seasonMatchText(payload)
                        + payload.get("matchTitle") + "」，请留意赛前通知。");
                notification.setRefType("MATCH");
                notification.setRefId(longValue(payload.get("matchId")));
            }
            case EventTypes.WAITLIST_PROMOTED -> {
                notification.setType(NotificationType.WAITLIST_PROMOTED);
                notification.setTitle("候补递补成功");
                notification.setBody("你已递补为「" + seasonMatchText(payload)
                        + payload.get("matchTitle") + "」的正式报名，请留意赛前通知。");
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
