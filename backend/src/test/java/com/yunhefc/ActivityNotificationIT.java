package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.domain.NotificationType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

class ActivityNotificationIT extends ActivityITSupport {

    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationEventConsumer notificationEventConsumer;

    @Test
    void createBroadcastsToActiveAccountsAndDeduplicatesConsumption() throws Exception {
        Cookie admin = bAdminLogin("13900002301");
        createPlayerUser("13900002302", "活动通知甲", 831, 1);
        createPlayerUser("13900002303", "活动通知乙", 832, 1);
        Long disabledUserId = insertUser("13900002304", "PLAYER");
        jdbc.update("UPDATE app_user SET status = 2 WHERE id = ?", disabledUserId);

        Long activityId = activityId(createActivity(admin, "通知活动", futureAt(30, 18), 4));

        Map<String, Object> event = latestEvent(EventTypes.ACTIVITY_CREATED, activityId);
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<String>read(payload, "$.activityTitle")).isEqualTo("通知活动");
        assertThat(JsonPath.<String>read(payload, "$.location")).isEqualTo("队内餐厅");

        consume(event, 301L);
        consume(event, 301L);

        for (String phone : new String[]{"13900002301", "13900002302", "13900002303"}) {
            assertThat(notificationCount(userId(phone), NotificationType.ACTIVITY_CREATED)).isEqualTo(1);
        }
        assertThat(notificationCount(disabledUserId, NotificationType.ACTIVITY_CREATED)).isZero();

        Map<String, Object> notification = latestNotification(userId("13900002302"));
        assertThat(notification.get("title")).isEqualTo("新活动");
        assertThat((String) notification.get("body")).contains("通知活动", "队内餐厅");
        assertThat(notification.get("ref_type")).isEqualTo("ACTIVITY");
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(activityId);
    }

    @Test
    void cancelNotifiesOnlyActiveSignupsWithReason() throws Exception {
        Cookie admin = bAdminLogin("13900002311");
        createPlayerUser("13900002312", "取消通知报名", 833, 1);
        createPlayerUser("13900002313", "取消通知已退", 834, 1);
        Long activityId = activityId(createActivity(admin, "取消通知活动", futureAt(30, 18), 4));
        Long activePlayer = playerId("13900002312");
        Long cancelledPlayer = playerId("13900002313");
        jdbc.update("""
                INSERT INTO activity_signup (activity_id, player_id, status)
                VALUES (?, ?, 0)
                """, activityId, activePlayer);
        jdbc.update("""
                INSERT INTO activity_signup (activity_id, player_id, status)
                VALUES (?, ?, 1)
                """, activityId, cancelledPlayer);

        mockMvc.perform(post("/api/v1/b/activities/" + activityId + "/status")
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":3,\"version\":0,\"reason\":\"餐厅临时停业\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3));

        Map<String, Object> event = latestEvent(EventTypes.ACTIVITY_CANCELLED, activityId);
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("餐厅临时停业");

        consume(event, 311L);
        consume(event, 311L);

        assertThat(notificationCount(userId("13900002312"), NotificationType.ACTIVITY_CANCELLED))
                .isEqualTo(1);
        assertThat(notificationCount(userId("13900002313"), NotificationType.ACTIVITY_CANCELLED))
                .isZero();

        Map<String, Object> notification = latestNotification(userId("13900002312"));
        assertThat(notification.get("title")).isEqualTo("活动取消");
        assertThat((String) notification.get("body"))
                .contains("取消通知活动", "餐厅临时停业");
        assertThat(notification.get("ref_type")).isEqualTo("ACTIVITY");
    }

    private void consume(Map<String, Object> row, long offset) throws Exception {
        OutboxMessage message = new OutboxMessage(
                (String) row.get("event_id"),
                row.get("team_id") == null ? null : ((Number) row.get("team_id")).longValue(),
                (String) row.get("aggregate_type"),
                ((Number) row.get("aggregate_id")).longValue(),
                (String) row.get("event_type"),
                (String) row.get("payload_json"));
        notificationEventConsumer.apply(objectMapper.writeValueAsString(message),
                NOTIFICATION_TOPIC, 0, offset);
    }

    private Map<String, Object> latestEvent(String eventType, Long activityId) {
        return jdbc.queryForMap("""
                SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json
                FROM outbox_event
                WHERE event_type = ? AND aggregate_id = ? AND is_deleted = 0
                ORDER BY id DESC LIMIT 1
                """, eventType, activityId);
    }

    private int notificationCount(Long userId, NotificationType type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ?",
                Integer.class, userId, type.code());
    }

    private Map<String, Object> latestNotification(Long userId) {
        return jdbc.queryForMap("""
                SELECT user_id, type, title, body, ref_type, ref_id
                FROM notification WHERE user_id = ? ORDER BY id DESC LIMIT 1
                """, userId);
    }
}
