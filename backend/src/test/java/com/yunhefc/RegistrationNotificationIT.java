package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RegistrationNotificationIT extends IntegrationTestSupport {

    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdMatchIds = new ArrayList<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationEventConsumer notificationEventConsumer;

    @AfterEach
    void cleanup() {
        for (Long matchId : createdMatchIds) {
            jdbc.update("DELETE FROM registration WHERE match_id = ?", matchId);
            jdbc.update("DELETE FROM team_match WHERE id = ?", matchId);
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notification WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void confirmedSignupWritesOutboxEventAndConsumesIntoOneNotification() throws Exception {
        Cookie admin = bAdminLogin("13900001301");
        Cookie player = playerLogin("13900001302", "报名通知甲");
        Long matchId = createMatch(admin, "M04报名通知", "2099-09-01T20:00:00");
        setCapacities(matchId, 2, 1, 1);

        signup(player, matchId).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(0));

        Long userId = userId("13900001302");
        // 通知只经 Outbox → Kafka → 消费者，业务事务不同步写 notification。
        assertThat(notificationCount(userId, NotificationType.REGISTRATION_CONFIRMED)).isZero();

        Map<String, Object> event = latestEvent(EventTypes.REGISTRATION_CONFIRMED, matchId);
        assertThat(((Number) event.get("team_id")).longValue()).isEqualTo(1L);
        assertThat(event.get("aggregate_type")).isEqualTo("MATCH");
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<Number>read(payload, "$.userId").longValue()).isEqualTo(userId);
        assertThat(JsonPath.<Number>read(payload, "$.matchId").longValue()).isEqualTo(matchId);
        assertThat(JsonPath.<String>read(payload, "$.matchTitle")).isEqualTo("M04报名通知");
        assertThat(payload).doesNotContain("13900001302");

        consume(event, 51L);
        consume(event, 51L);

        Map<String, Object> notification = latestNotification(userId);
        assertThat(((Number) notification.get("type")).intValue())
                .isEqualTo(NotificationType.REGISTRATION_CONFIRMED.code());
        assertThat(notification.get("ref_type")).isEqualTo("MATCH");
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(matchId);
        assertThat(notification.get("title")).isEqualTo("报名成功");
        assertThat((String) notification.get("body")).contains("M04报名通知");
        assertThat(notification.get("title") + (String) notification.get("body"))
                .doesNotContain("13900001302");
        assertThat(notificationCount(userId, NotificationType.REGISTRATION_CONFIRMED)).isEqualTo(1);
    }

    @Test
    void waitlistedSignupDoesNotWriteNotificationEvent() throws Exception {
        Cookie admin = bAdminLogin("13900001303");
        Cookie confirmed = playerLogin("13900001304", "候补外确认");
        Cookie waitlisted = playerLogin("13900001305", "候补不发通知");
        Long matchId = createMatch(admin, "M04候补通知", "2099-09-02T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(jsonPath("$.data.registration.status").value(0));
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        Long waitlistedUserId = userId("13900001305");
        assertThat(eventCount(EventTypes.REGISTRATION_CONFIRMED, matchId, waitlistedUserId)).isZero();
        assertThat(eventCount(EventTypes.WAITLIST_PROMOTED, matchId, waitlistedUserId)).isZero();
        assertThat(notificationCount(waitlistedUserId, NotificationType.REGISTRATION_CONFIRMED))
                .isZero();
    }

    @Test
    void cancelPromotionNotifiesOnlyPromotedPlayer() throws Exception {
        Cookie admin = bAdminLogin("13900001306");
        Cookie confirmed = playerLogin("13900001307", "取消通知确认");
        Cookie earlier = playerLogin("13900001308", "取消通知递补早");
        Cookie later = playerLogin("13900001309", "取消通知递补晚");
        Long matchId = createMatch(admin, "M04取消通知", "2099-09-03T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(earlier, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(later, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        mockMvc.perform(delete("/api/v1/c/matches/" + matchId + "/registrations/me")
                        .cookie(confirmed)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promoted").value(true));

        assertPromotionNotifiesOnlyPromoted(matchId, "13900001307", "13900001308",
                "13900001309", 61L);
    }

    @Test
    void adminRemovalPromotionNotifiesOnlyPromotedPlayer() throws Exception {
        Cookie admin = bAdminLogin("13900001310");
        Cookie confirmed = playerLogin("13900001311", "移除通知确认");
        Cookie earlier = playerLogin("13900001312", "移除通知递补早");
        Cookie later = playerLogin("13900001313", "移除通知递补晚");
        Long matchId = createMatch(admin, "M04移除通知", "2099-09-04T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(earlier, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(later, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        Long registrationId = jdbc.queryForObject("""
                SELECT id FROM registration WHERE match_id = ? AND player_id = ? AND status = 0
                """, Long.class, matchId, playerId("13900001311"));

        mockMvc.perform(delete("/api/v1/b/registrations/" + registrationId)
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.promoted").value(true));

        assertPromotionNotifiesOnlyPromoted(matchId, "13900001311", "13900001312",
                "13900001313", 71L);
    }

    @Test
    void disablePromotionNotifiesOnlyPromotedPlayer() throws Exception {
        Cookie admin = bAdminLogin("13900001314");
        Cookie confirmed = playerLogin("13900001315", "注销通知确认");
        Cookie earlier = playerLogin("13900001316", "注销通知递补早");
        Cookie later = playerLogin("13900001317", "注销通知递补晚");
        Long matchId = createMatch(admin, "M04注销通知", "2099-09-05T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(earlier, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(later, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        mockMvc.perform(post("/api/v1/b/players/" + playerId("13900001315") + "/disable")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"M04注销递补通知\"}")
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        assertPromotionNotifiesOnlyPromoted(matchId, "13900001315", "13900001316",
                "13900001317", 81L);
    }

    private void assertPromotionNotifiesOnlyPromoted(Long matchId, String confirmedPhone,
                                                     String earlierPhone, String laterPhone,
                                                     long offset) throws Exception {
        Long earlierUserId = userId(earlierPhone);
        assertThat(eventCount(EventTypes.WAITLIST_PROMOTED, matchId, earlierUserId)).isEqualTo(1);
        assertThat(eventCount(EventTypes.WAITLIST_PROMOTED, matchId, userId(confirmedPhone))).isZero();
        assertThat(eventCount(EventTypes.WAITLIST_PROMOTED, matchId, userId(laterPhone))).isZero();

        Map<String, Object> event = latestEvent(EventTypes.WAITLIST_PROMOTED, matchId);
        assertThat(event.get("aggregate_type")).isEqualTo("MATCH");
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<String>read(payload, "$.matchTitle")).isEqualTo(matchTitle(matchId));
        assertThat(payload).doesNotContain(confirmedPhone, earlierPhone, laterPhone);

        consume(event, offset);

        Map<String, Object> notification = latestNotification(earlierUserId);
        assertThat(((Number) notification.get("type")).intValue())
                .isEqualTo(NotificationType.WAITLIST_PROMOTED.code());
        assertThat(notification.get("ref_type")).isEqualTo("MATCH");
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(matchId);
        assertThat(notification.get("title")).isEqualTo("候补递补成功");
        assertThat((String) notification.get("body")).contains(matchTitle(matchId));
        assertThat(notification.get("title") + (String) notification.get("body"))
                .doesNotContain(confirmedPhone, earlierPhone, laterPhone);

        assertThat(notificationCount(userId(confirmedPhone), NotificationType.WAITLIST_PROMOTED))
                .isZero();
        assertThat(notificationCount(userId(laterPhone), NotificationType.WAITLIST_PROMOTED))
                .isZero();
        assertThat(notificationCount(earlierUserId, NotificationType.WAITLIST_PROMOTED))
                .isEqualTo(1);
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

    private Map<String, Object> latestEvent(String eventType, Long matchId) {
        return jdbc.queryForMap("""
                SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json
                FROM outbox_event
                WHERE event_type = ? AND aggregate_id = ? AND is_deleted = 0
                ORDER BY id DESC LIMIT 1
                """, eventType, matchId);
    }

    private int eventCount(String eventType, Long matchId, Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = ? AND aggregate_id = ? AND is_deleted = 0
                  AND CAST(JSON_EXTRACT(payload_json, '$.userId') AS UNSIGNED) = ?
                """, Integer.class, eventType, matchId, userId);
    }

    private Map<String, Object> latestNotification(Long userId) {
        return jdbc.queryForMap("""
                SELECT user_id, type, title, body, ref_type, ref_id
                FROM notification WHERE user_id = ? ORDER BY id DESC LIMIT 1
                """, userId);
    }

    private int notificationCount(Long userId, NotificationType type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ?",
                Integer.class, userId, type.code());
    }

    private String matchTitle(Long matchId) {
        return jdbc.queryForObject("SELECT title FROM team_match WHERE id = ?", String.class, matchId);
    }

    private ResultActions signup(Cookie cookie, Long matchId) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private void setCapacities(Long matchId, int capacity, int guestCapacity, int waitlistCapacity) {
        jdbc.update("""
                UPDATE team_match
                SET capacity = ?, guest_capacity = ?, waitlist_capacity = ?
                WHERE id = ?
                """, capacity, guestCapacity, waitlistCapacity, matchId);
    }

    private Long createMatch(Cookie admin, String title, String startAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","type":1,"season":"2099","startAt":"%s","endAt":"%s",
                                 "venue":"聚龙足球场","homeTeamName":"橙队","awayTeamName":"兰队"}
                                """.formatted(title, local(startAt), local(
                                LocalDateTime.parse(startAt).plusHours(2).toString()))))
                .andExpect(status().isOk())
                .andReturn();
        Long matchId = JsonPath.<Number>read(
                result.getResponse().getContentAsString(), "$.data.id").longValue();
        createdMatchIds.add(matchId);
        return matchId;
    }

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 1, roleId("PLAYER"));
        return cLogin(phone, "Player123!");
    }

    private void insertPlayer(String phone, String name, int type, Long roleId) {
        Long userId = insertUser(phone, 1, roleId);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 1, '[]', 3)
                """, userId, name, type);
    }

    private Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private Cookie bAdminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Admin123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Admin123!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private Cookie cLogin(String phone, String password) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }
}
