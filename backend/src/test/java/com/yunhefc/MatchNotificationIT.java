package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.domain.NotificationType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class MatchNotificationIT extends IntegrationTestSupport {

    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";
    private static final String MATCH_POSTPONED = EventTypes.MATCH_POSTPONED;
    private static final String MATCH_CANCELLED = EventTypes.MATCH_CANCELLED;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdMatchIds = new ArrayList<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationEventConsumer notificationEventConsumer;

    @MockitoSpyBean
    private OutboxService outboxService;

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
    void postponeNotifiesEveryActiveRegistrationOnceAndExcludesInactive() throws Exception {
        Cookie admin = bAdminLogin("13900001501");
        Long confirmed = playerId(insertPlayer("13900001502", "延期确认"));
        Long waitlisted = playerId(insertPlayer("13900001503", "延期候补"));
        Long cancelled = playerId(insertPlayer("13900001504", "延期已取消"));
        Long removed = playerId(insertPlayer("13900001505", "延期已移除"));
        Long matchId = createMatch(admin, "M04延期通知", "2026-12-19T20:00:00");
        insertRegistration(matchId, confirmed, 1, 0);
        insertRegistration(matchId, waitlisted, 1, 1);
        insertRegistration(matchId, cancelled, 1, 2);
        insertRegistration(matchId, removed, 1, 3);

        postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5));

        Map<String, Object> event = latestEvent(MATCH_POSTPONED, matchId);
        assertThat(event.get("aggregate_type")).isEqualTo("MATCH");
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<Number>read(payload, "$.matchId").longValue()).isEqualTo(matchId);
        assertThat(JsonPath.<String>read(payload, "$.matchTitle")).isEqualTo("M04延期通知");
        assertThat(JsonPath.<Boolean>read(payload, "$.rescheduled")).isFalse();
        assertThat(JsonPath.<String>read(payload, "$.startAt"))
                .isEqualTo(local("2026-12-20T19:00:00").toString());
        assertThat(payload).doesNotContain("13900001502", "13900001503");

        consume(event, 101L);
        consume(event, 101L);

        assertThat(notificationCount(userId("13900001502"), NotificationType.MATCH_POSTPONED)).isEqualTo(1);
        assertThat(notificationCount(userId("13900001503"), NotificationType.MATCH_POSTPONED)).isEqualTo(1);
        assertThat(notificationCount(userId("13900001504"), NotificationType.MATCH_POSTPONED)).isZero();
        assertThat(notificationCount(userId("13900001505"), NotificationType.MATCH_POSTPONED)).isZero();

        Map<String, Object> notification = latestNotification(userId("13900001503"));
        assertThat(notification.get("title")).isEqualTo("比赛延期");
        assertThat((String) notification.get("body"))
                .contains("M04延期通知", "12月20日 19:00")
                .doesNotContain("13900001503");
        assertThat(notification.get("ref_type")).isEqualTo("MATCH");
        assertThat(((Number) notification.get("ref_id")).longValue()).isEqualTo(matchId);
    }

    @Test
    void rescheduleReusesPostponedTypeWithUpdatedTime() throws Exception {
        Cookie admin = bAdminLogin("13900001511");
        Long player = playerId(insertPlayer("13900001512", "重新排期球员"));
        Long matchId = createMatch(admin, "M04重新排期通知", "2026-12-19T20:00:00");
        insertRegistration(matchId, player, 1, 0);

        postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isOk());
        reschedule(admin, matchId, "2026-12-25T18:00:00", "2026-12-25T21:00:00", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0));

        assertThat(eventCount(MATCH_POSTPONED, matchId)).isEqualTo(2);
        Map<String, Object> event = latestEvent(MATCH_POSTPONED, matchId);
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<Boolean>read(payload, "$.rescheduled")).isTrue();
        assertThat(JsonPath.<String>read(payload, "$.startAt"))
                .isEqualTo(local("2026-12-25T18:00:00").toString());

        consume(event, 111L);

        Map<String, Object> notification = latestNotification(userId("13900001512"));
        assertThat(((Number) notification.get("type")).intValue())
                .isEqualTo(NotificationType.MATCH_POSTPONED.code());
        assertThat(notification.get("title")).isEqualTo("比赛恢复报名");
        assertThat((String) notification.get("body"))
                .contains("M04重新排期通知", "重新排期", "12月25日 18:00");
        assertThat(notificationCount(userId("13900001512"), NotificationType.MATCH_POSTPONED))
                .isEqualTo(1);
    }

    @Test
    void cancelNotifiesRegistrantsWithReason() throws Exception {
        Cookie admin = bAdminLogin("13900001521");
        Long confirmed = playerId(insertPlayer("13900001522", "取消通知确认"));
        Long waitlisted = playerId(insertPlayer("13900001523", "取消通知候补"));
        Long matchId = createMatch(admin, "M04取消通知", "2026-12-19T20:00:00");
        insertRegistration(matchId, confirmed, 1, 0);
        insertRegistration(matchId, waitlisted, 1, 1);

        cancel(admin, matchId, "  场地临时封闭  ", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6));

        Map<String, Object> event = latestEvent(MATCH_CANCELLED, matchId);
        String payload = (String) event.get("payload_json");
        assertThat(JsonPath.<Number>read(payload, "$.matchId").longValue()).isEqualTo(matchId);
        assertThat(JsonPath.<String>read(payload, "$.matchTitle")).isEqualTo("M04取消通知");
        assertThat(JsonPath.<String>read(payload, "$.reason")).isEqualTo("场地临时封闭");
        assertThat(payload).doesNotContain("13900001522", "13900001523");

        consume(event, 121L);

        Map<String, Object> notification = latestNotification(userId("13900001522"));
        assertThat(((Number) notification.get("type")).intValue())
                .isEqualTo(NotificationType.MATCH_CANCELLED.code());
        assertThat(notification.get("title")).isEqualTo("比赛取消");
        assertThat((String) notification.get("body"))
                .contains("M04取消通知", "场地临时封闭")
                .doesNotContain("13900001522");
        assertThat(notificationCount(userId("13900001523"), NotificationType.MATCH_CANCELLED))
                .isEqualTo(1);
    }

    @Test
    void otherStatusChangesDoNotNotifyRegistrants() throws Exception {
        Cookie admin = bAdminLogin("13900001531");
        Long player = playerId(insertPlayer("13900001532", "普通流转球员"));
        Long matchId = createMatch(admin, "M04普通流转", "2026-12-19T20:00:00");
        insertRegistration(matchId, player, 1, 0);

        changeStatus(admin, matchId, "{\"status\":1,\"version\":0}", 1);
        changeStatus(admin, matchId, "{\"status\":2,\"version\":1}", 2);
        changeStatus(admin, matchId, "{\"status\":3,\"version\":2}", 3);
        changeStatus(admin, matchId, "{\"status\":4,\"version\":3}", 4);

        assertThat(eventCount(MATCH_POSTPONED, matchId)).isZero();
        assertThat(eventCount(MATCH_CANCELLED, matchId)).isZero();
        assertThat(notificationCount(userId("13900001532"), NotificationType.MATCH_POSTPONED)).isZero();
        assertThat(notificationCount(userId("13900001532"), NotificationType.MATCH_CANCELLED)).isZero();
    }

    @Test
    void publicStatusEntryPostponeAndCancelBothNotify() throws Exception {
        Cookie admin = bAdminLogin("13900001541");
        Cookie cAdmin = cAdminLogin("13900001541");
        Long player = playerId(insertPlayer("13900001542", "C端通知球员"));
        Long matchId = createMatch(admin, "M04C端改期", "2026-12-19T20:00:00");
        insertRegistration(matchId, player, 1, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/status")
                        .cookie(cAdmin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":5,"version":0,"startAt":"%s","endAt":"%s"}
                                """.formatted(local("2026-12-20T19:00:00"),
                                local("2026-12-20T22:00:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5));

        Map<String, Object> postponed = latestEvent(MATCH_POSTPONED, matchId);
        assertThat(JsonPath.<Boolean>read((String) postponed.get("payload_json"), "$.rescheduled"))
                .isFalse();
        consume(postponed, 141L);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/status")
                        .cookie(cAdmin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":6,"version":1,"reason":"C 端取消"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6));

        Map<String, Object> cancelled = latestEvent(MATCH_CANCELLED, matchId);
        assertThat(JsonPath.<String>read((String) cancelled.get("payload_json"), "$.reason"))
                .isEqualTo("C 端取消");
        consume(cancelled, 142L);

        Long userId = userId("13900001542");
        assertThat(notificationCount(userId, NotificationType.MATCH_POSTPONED)).isEqualTo(1);
        assertThat(notificationCount(userId, NotificationType.MATCH_CANCELLED)).isEqualTo(1);
    }

    @Test
    void notificationEventRollsBackWithStatusChange() throws Exception {
        Cookie admin = bAdminLogin("13900001551");
        Long player = playerId(insertPlayer("13900001552", "延期回滚球员"));
        Long matchId = createMatch(admin, "M04延期回滚", "2026-12-19T20:00:00");
        insertRegistration(matchId, player, 1, 0);

        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService).append(any(), any(), any(), eq(EventTypes.MATCH_POSTPONED), any());

        postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(eventCount(MATCH_POSTPONED, matchId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_POSTPONE'
                """, Integer.class, matchId)).isZero();
    }

    private void changeStatus(Cookie cookie, Long matchId, String body, int expectedVersion)
            throws Exception {
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/status")
                        .cookie(cookie)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(expectedVersion));
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

    private int eventCount(String eventType, Long matchId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = ? AND aggregate_id = ? AND is_deleted = 0
                """, Integer.class, eventType, matchId);
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

    private org.springframework.test.web.servlet.ResultActions postpone(
            Cookie admin, Long matchId, String startAt, String endAt, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/postpone")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt":"%s","endAt":"%s","version":%d}
                        """.formatted(local(startAt), local(endAt), version)));
    }

    private org.springframework.test.web.servlet.ResultActions reschedule(
            Cookie admin, Long matchId, String startAt, String endAt, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/reschedule")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt":"%s","endAt":"%s","version":%d}
                        """.formatted(local(startAt), local(endAt), version)));
    }

    private org.springframework.test.web.servlet.ResultActions cancel(
            Cookie admin, Long matchId, String reason, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/cancel")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"%s","version":%d}
                        """.formatted(reason, version)));
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
                                """.formatted(title, local(startAt),
                                local(LocalDateTime.parse(startAt).plusHours(2).toString()))))
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

    private void insertRegistration(Long matchId, Long playerId, int type, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, NOW(3))
                """, matchId, playerId, type, status);
    }

    private Long insertPlayer(String phone, String name) {
        Long userId = insertUser(phone, roleId("PLAYER"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, 1, 1, '[]', 3)
                """, userId, name);
        return userId;
    }

    private Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Long playerId(Long userId) {
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = userId(phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private Cookie bAdminLogin(String phone) throws Exception {
        insertUser(phone, roleId("ADMIN"));
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

    private Cookie cAdminLogin(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Admin123!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }
}
