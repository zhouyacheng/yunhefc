package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.domain.NotificationType;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

class NotificationIT extends IntegrationTestSupport {

    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";
    private static final String PLAYER_PHONE = "13900002001";
    private static final String PLAYER_PASSWORD = "Player123!";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationEventConsumer notificationEventConsumer;

    @Autowired
    private NotificationMapper notificationMapper;

    @Test
    void listReturnsOwnNotificationsWithFixedOrderAndUnreadFilter() throws Exception {
        Long userId = activePlayerUser(PLAYER_PHONE, "通知列表甲");
        Long otherUserId = activePlayerUser("13900002002", "通知列表乙");
        Instant base = Instant.parse("2026-11-01T10:00:00Z");
        Long readOld = insertNotification(userId, NotificationType.MATCH_CANCELLED, "已读旧通知",
                "旧的已读通知", "MATCH", 11L, base.minus(2, ChronoUnit.HOURS), base);
        Long older = insertNotification(userId, NotificationType.REGISTRATION_CONFIRMED, "报名成功",
                "较早的未读通知", "MATCH", 12L, base, null);
        Long newer = insertNotification(userId, NotificationType.ROLE_CHANGED, "角色变更",
                "较新的未读通知", "USER", userId, base.plus(1, ChronoUnit.HOURS), null);
        Long sameTimeNewer = insertNotification(userId, NotificationType.ACCOUNT_APPROVED, "账号审核通过",
                "同时间后写入的未读通知", "USER", userId, base.plus(1, ChronoUnit.HOURS), null);
        Long otherNotification = insertNotification(otherUserId, NotificationType.ROLE_CHANGED, "他人通知",
                "不应出现在本列表", "USER", otherUserId, base, null);
        Cookie player = cLogin(PLAYER_PHONE, PLAYER_PASSWORD);

        mockMvc.perform(get("/api/v1/c/notifications").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items[0].id").value(sameTimeNewer))
                .andExpect(jsonPath("$.data.items[0].type").value(NotificationType.ACCOUNT_APPROVED.code()))
                .andExpect(jsonPath("$.data.items[0].typeLabel").value("ACCOUNT_APPROVED"))
                .andExpect(jsonPath("$.data.items[0].refType").value("USER"))
                .andExpect(jsonPath("$.data.items[0].refId").value(userId))
                .andExpect(jsonPath("$.data.items[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].id").value(newer))
                .andExpect(jsonPath("$.data.items[2].id").value(older))
                .andExpect(jsonPath("$.data.items[3].id").value(readOld))
                .andExpect(jsonPath("$.data.items[3].readAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[?(@.id == " + otherNotification + ")]").isEmpty());

        mockMvc.perform(get("/api/v1/c/notifications").param("unread", "true").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(sameTimeNewer))
                .andExpect(jsonPath("$.data.items[1].id").value(newer))
                .andExpect(jsonPath("$.data.items[2].id").value(older));

        mockMvc.perform(get("/api/v1/c/notifications").param("unread", "false").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4));
    }

    @Test
    void unreadCountAgreesWithMeAndReadAllOnlyTouchesOwnRows() throws Exception {
        Long userId = activePlayerUser(PLAYER_PHONE, "未读统计甲");
        Long otherUserId = activePlayerUser("13900002003", "未读统计乙");
        Instant now = Instant.now();
        Long first = insertNotification(userId, NotificationType.REGISTRATION_CONFIRMED, "报名成功",
                "未读一", "MATCH", 21L, now, null);
        insertNotification(userId, NotificationType.WAITLIST_PROMOTED, "候补递补成功",
                "未读二", "MATCH", 22L, now, null);
        insertNotification(userId, NotificationType.ROLE_CHANGED, "角色变更",
                "已读", "USER", userId, now, now);
        Long otherNotification = insertNotification(otherUserId, NotificationType.ROLE_CHANGED, "角色变更",
                "他人未读", "USER", otherUserId, now, null);
        Cookie player = cLogin(PLAYER_PHONE, PLAYER_PASSWORD);

        mockMvc.perform(get("/api/v1/c/notifications/unread-count").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));
        mockMvc.perform(get("/api/v1/c/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        MvcResult read = mockMvc.perform(post("/api/v1/c/notifications/" + first + "/read")
                        .cookie(player).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty())
                .andReturn();
        String firstReadAt = JsonPath.read(read.getResponse().getContentAsString(), "$.data.readAt");

        MvcResult repeated = mockMvc.perform(post("/api/v1/c/notifications/" + first + "/read")
                        .cookie(player).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String repeatedReadAt = JsonPath.read(repeated.getResponse().getContentAsString(), "$.data.readAt");
        assertThat(repeatedReadAt).isEqualTo(firstReadAt);

        mockMvc.perform(get("/api/v1/c/notifications/unread-count").cookie(player))
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/v1/c/notifications/read-all").cookie(player).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));

        mockMvc.perform(get("/api/v1/c/notifications/unread-count").cookie(player))
                .andExpect(jsonPath("$.data.count").value(0));
        mockMvc.perform(post("/api/v1/c/notifications/read-all").cookie(player).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(0));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ? AND read_at IS NULL",
                Integer.class, otherNotification)).isEqualTo(1);
    }

    @Test
    void markingOtherUsersOrMissingNotificationReturns40401() throws Exception {
        Long userId = activePlayerUser(PLAYER_PHONE, "越权甲");
        Long otherUserId = activePlayerUser("13900002004", "越权乙");
        Long otherNotification = insertNotification(otherUserId, NotificationType.ROLE_CHANGED, "角色变更",
                "他人通知", "USER", otherUserId, Instant.now(), null);
        Cookie player = cLogin(PLAYER_PHONE, PLAYER_PASSWORD);

        mockMvc.perform(post("/api/v1/c/notifications/" + otherNotification + "/read")
                        .cookie(player).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(post("/api/v1/c/notifications/999999999/read")
                        .cookie(player).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ? AND read_at IS NULL",
                Integer.class, otherNotification)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND read_at IS NOT NULL",
                Integer.class, userId)).isZero();
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/c/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/api/v1/c/notifications/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(post("/api/v1/c/notifications/read-all").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void pendingAccountCanListCountAndMarkRead() throws Exception {
        Long userId = insertUser("13900002005", UserStatus.PENDING, "GUEST");
        Long first = insertNotification(userId, NotificationType.ACCOUNT_APPROVED, "账号审核通过",
                "待审核可读", "USER", userId, Instant.now(), null);
        insertNotification(userId, NotificationType.ROLE_CHANGED, "角色变更",
                "待审核可读二", "USER", userId, Instant.now(), null);
        Cookie pending = cLogin("13900002005", PLAYER_PASSWORD);

        mockMvc.perform(get("/api/v1/c/notifications").cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mockMvc.perform(get("/api/v1/c/notifications/unread-count").cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));
        mockMvc.perform(post("/api/v1/c/notifications/" + first + "/read")
                        .cookie(pending).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        mockMvc.perform(post("/api/v1/c/notifications/read-all").cookie(pending).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1));
        mockMvc.perform(get("/api/v1/c/notifications/unread-count").cookie(pending))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void convertEmitsSingleRoleChangedNotificationForPromotedUser() throws Exception {
        Cookie admin = bAdminLogin("13900002006");
        Long userId = insertUser("13900002007", UserStatus.ACTIVE, "GUEST");
        Long playerId = insertPlayer(userId, "转正通知甲", 0, PlayerType.GUEST.code());

        convert(admin, playerId, "{\"jersey\":8}", "notification-convert-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value(PlayerType.PLAYER.code()));

        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json
                FROM outbox_event
                WHERE event_type = 'ROLE_CHANGED' AND aggregate_type = 'USER' AND aggregate_id = ?
                """, userId);
        assertThat(events).hasSize(1);
        Map<String, Object> event = events.get(0);
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<Number>read(payload, "$.userId").longValue()).isEqualTo(userId);
        assertThat(JsonPath.<List<String>>read(payload, "$.roles")).containsExactly("PLAYER");

        OutboxMessage message = toMessage(event);
        notificationEventConsumer.apply(objectMapper.writeValueAsString(message), NOTIFICATION_TOPIC, 0, 701L);
        notificationEventConsumer.apply(objectMapper.writeValueAsString(message), NOTIFICATION_TOPIC, 0, 701L);

        assertThat(notificationCount(userId, NotificationType.ROLE_CHANGED)).isEqualTo(1);
        assertThat(notificationCount(userIdOf("13900002006"), NotificationType.ROLE_CHANGED)).isZero();
        Map<String, Object> notification = jdbc.queryForMap("""
                SELECT type, title, body, ref_type, ref_id FROM notification
                WHERE user_id = ? AND type = ?
                """, userId, NotificationType.ROLE_CHANGED.code());
        assertThat(notification.get("title")).isEqualTo("角色变更");
        assertThat((String) notification.get("body")).contains("PLAYER").doesNotContain("GUEST");
        assertThat(notification.get("ref_type")).isEqualTo("USER");
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(userId);

        convert(admin, playerId, "{}", "notification-convert-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value(PlayerType.PLAYER.code()));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'ROLE_CHANGED' AND aggregate_type = 'USER' AND aggregate_id = ?
                """, Integer.class, userId)).isEqualTo(1);
        assertThat(notificationCount(userId, NotificationType.ROLE_CHANGED)).isEqualTo(1);
    }

    @Test
    void deleteCreatedBeforePhysicallyRemovesExpiredNotifications() {
        Long userId = insertUser("13900002008", UserStatus.ACTIVE, "PLAYER");
        Instant now = Instant.now();
        Long expired = insertNotification(userId, NotificationType.ROLE_CHANGED, "过期通知",
                "满 90 天", "USER", userId, now.minus(91, ChronoUnit.DAYS), null);
        Long retained = insertNotification(userId, NotificationType.ROLE_CHANGED, "保留通知",
                "未满 90 天", "USER", userId, now.minus(89, ChronoUnit.DAYS), null);

        int deleted = notificationMapper.deleteCreatedBefore(now.minus(90, ChronoUnit.DAYS), 1000);

        assertThat(deleted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ?",
                Integer.class, expired)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id = ?",
                Integer.class, retained)).isEqualTo(1);
    }

    private ResultActions convert(Cookie actor, Long playerId, String body, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/players/" + playerId + "/convert")
                .cookie(actor)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Idempotency-Key", idempotencyKey));
    }

    private Long insertNotification(Long userId, NotificationType type, String title, String body,
                                    String refType, Long refId, Instant createdAt, Instant readAt) {
        jdbc.update("""
                INSERT INTO notification (user_id, type, title, body, ref_type, ref_id, read_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, type.code(), title, body, refType, refId,
                readAt == null ? null : Timestamp.from(readAt), Timestamp.from(createdAt));
        return jdbc.queryForObject("SELECT MAX(id) FROM notification WHERE user_id = ?", Long.class, userId);
    }

    private Long activePlayerUser(String phone, String name) {
        Long userId = insertUser(phone, UserStatus.ACTIVE, "PLAYER");
        insertPlayer(userId, name, null, PlayerType.PLAYER.code());
        return userId;
    }

    private Long insertUser(String phone, UserStatus status, String roleCode) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status.code());
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId(roleCode));
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(PLAYER_PASSWORD), userId);
        return userId;
    }

    private Long insertPlayer(Long userId, String name, Integer jersey, int type) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, 1, '[]', 3)
                """, userId, name, jersey, type);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long userIdOf(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private int notificationCount(Long userId, NotificationType type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ?",
                Integer.class, userId, type.code());
    }

    private OutboxMessage toMessage(Map<String, Object> row) {
        return new OutboxMessage(
                (String) row.get("event_id"),
                row.get("team_id") == null ? null : ((Number) row.get("team_id")).longValue(),
                (String) row.get("aggregate_type"),
                ((Number) row.get("aggregate_id")).longValue(),
                (String) row.get("event_type"),
                (String) row.get("payload_json"));
    }

    private Cookie bAdminLogin(String phone) throws Exception {
        Long userId = insertUser(phone, UserStatus.ACTIVE, "ADMIN");
        assertThat(userId).isNotNull();
        return login("/api/v1/b/auth/login", "YHFC_B", phone);
    }

    private Cookie cLogin(String phone, String password) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        return login("/api/v1/c/auth/login", "YHFC_C", phone);
    }

    private Cookie login(String path, String cookieName, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, PLAYER_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, cookieName);
    }
}
