package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.domain.NotificationType;
import com.yunhefc.registration.domain.RegistrationStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PlayerDisableIT extends IntegrationTestSupport {

    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";

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
    void disableCancelsUnstartedRegistrationsPromotesEarliestWaitlistAndNotifies() throws Exception {
        Cookie admin = adminLogin("13900001001");
        Long adminId = userId("13900001001");
        Long userId = insertUser("13900001011", UserStatus.ACTIVE.code(), roleId("PLAYER"));
        Long playerId = insertPlayer(userId, "M02注销甲", PlayerType.PLAYER.code());
        Long earlierUserId = insertUser("13900001012", UserStatus.ACTIVE.code(), roleId("PLAYER"));
        Long earlierPlayerId = insertPlayer(earlierUserId, "M02候补甲", PlayerType.PLAYER.code());
        Long laterUserId = insertUser("13900001013", UserStatus.ACTIVE.code(), roleId("PLAYER"));
        Long laterPlayerId = insertPlayer(laterUserId, "M02候补乙", PlayerType.PLAYER.code());

        Long lockedMatch = insertMatch("M02注销锁定场", 2, 100);
        Long inProgressMatch = insertMatch("M02注销进行中场", 3, 101);
        Long openMatch = insertMatch("M02注销报名中场", 0, 102);
        insertRegistration(lockedMatch, playerId, 1, 0, "2026-11-01 08:00:00");
        insertRegistration(lockedMatch, earlierPlayerId, 1, 1, "2026-11-01 09:00:00");
        insertRegistration(lockedMatch, laterPlayerId, 1, 1, "2026-11-01 10:00:00");
        insertRegistration(inProgressMatch, playerId, 1, 0, "2026-11-01 08:00:00");
        insertRegistration(openMatch, playerId, 1, 1, "2026-11-01 08:00:00");
        insertRegistration(openMatch, laterPlayerId, 1, 1, "2026-11-01 09:00:00");

        disable(admin, playerId, "{\"reason\":\"M02注销原因\"}", "disable-1011")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(playerId))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.statusLabel").value("DISABLED"))
                .andExpect(jsonPath("$.data.wechatBound").value(false));

        assertThat(userStatus(userId)).isEqualTo(UserStatus.DISABLED.code());
        assertRegistration(lockedMatch, playerId, RegistrationStatus.CANCELLED.code(), adminId);
        assertRegistration(lockedMatch, earlierPlayerId, RegistrationStatus.CONFIRMED.code(), null);
        assertRegistration(lockedMatch, laterPlayerId, RegistrationStatus.WAITLISTED.code(), null);
        assertRegistration(inProgressMatch, playerId, RegistrationStatus.CONFIRMED.code(), null);
        assertRegistration(openMatch, playerId, RegistrationStatus.CANCELLED.code(), adminId);
        assertRegistration(openMatch, laterPlayerId, RegistrationStatus.WAITLISTED.code(), null);

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) AS action,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetType')) AS target_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.beforeJson')) AS before_json,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) AS after_json
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, userId);
        assertThat(audit.get("action")).isEqualTo("USER_DISABLE");
        assertThat(audit.get("target_type")).isEqualTo("USER");
        assertThat((String) audit.get("before_json")).contains("\"status\":1");
        assertThat((String) audit.get("after_json"))
                .contains("\"status\":2", "\"reason\":\"M02注销原因\"");

        Map<String, Object> disabled = jdbc.queryForMap("""
                SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json
                FROM outbox_event
                WHERE event_type = 'ACCOUNT_DISABLED' AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, userId);
        assertThat(disabled.get("aggregate_type")).isEqualTo("USER");

        String message = objectMapper.writeValueAsString(toMessage(disabled));
        notificationEventConsumer.apply(message, NOTIFICATION_TOPIC, 0, 41L);
        notificationEventConsumer.apply(message, NOTIFICATION_TOPIC, 0, 41L);

        int disabledType = NotificationType.ACCOUNT_DISABLED.code();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ?",
                Integer.class, userId, disabledType)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM notification WHERE user_id = ? AND type = ?",
                String.class, userId, disabledType)).isEqualTo("账号已注销");
    }

    @Test
    void pendingAccountCannotBeDisabled() throws Exception {
        Cookie admin = adminLogin("13900001002");
        Long userId = insertUser("13900001014", UserStatus.PENDING.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02注销乙", PlayerType.GUEST.code());

        disable(admin, playerId, "{\"reason\":\"待审核\"}", "disable-1014")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        assertThat(userStatus(userId)).isEqualTo(UserStatus.PENDING.code());
        assertThat(countAudit(userId)).isZero();
        assertThat(countOutbox(userId)).isZero();
    }

    @Test
    void disablingAlreadyDisabledAccountIsIdempotent() throws Exception {
        Cookie admin = adminLogin("13900001003");
        Long userId = insertUser("13900001015", UserStatus.DISABLED.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02注销丙", PlayerType.GUEST.code());

        disable(admin, playerId, "{\"reason\":\"重复注销\"}", "disable-1015")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));

        assertThat(userStatus(userId)).isEqualTo(UserStatus.DISABLED.code());
        assertThat(countAudit(userId)).isZero();
        assertThat(countOutbox(userId)).isZero();
    }

    @Test
    void lastAdminCannotBeDisabled() throws Exception {
        Cookie admin = adminLogin("13900001004");
        Long adminId = userId("13900001004");
        Long playerId = insertPlayer(adminId, "M02最后管理员", PlayerType.PLAYER.code());

        disable(admin, playerId, "{\"reason\":\"自注销\"}", "disable-1016")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40913));

        assertThat(userStatus(adminId)).isEqualTo(UserStatus.ACTIVE.code());
        assertThat(countAudit(adminId)).isZero();
    }

    @Test
    void oldSessionIsRejectedAfterDisable() throws Exception {
        Cookie admin = adminLogin("13900001005");
        Long userId = insertUser("13900001017", UserStatus.ACTIVE.code(), roleId("PLAYER"));
        Long playerId = insertPlayer(userId, "M02注销丁", PlayerType.PLAYER.code());
        Cookie victim = login("13900001017", "Victim123!", "/api/v1/c/auth/login", "YHFC_C");

        mockMvc.perform(get("/api/v1/c/me").cookie(victim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(userId));

        disable(admin, playerId, "{\"reason\":\"会话失效\"}", "disable-1017")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/c/me").cookie(victim))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void disableRequiresPermissionIdempotencyKeyAndReason() throws Exception {
        Long viewerRole = insertRole("M02_DISABLE_VIEWER", "player:view:any");
        insertUser("13900001018", UserStatus.ACTIVE.code(), viewerRole);
        Cookie viewer = login("13900001018", "Viewer123!", "/api/v1/b/auth/login", "YHFC_B");
        Cookie admin = adminLogin("13900001006");
        Long userId = insertUser("13900001019", UserStatus.ACTIVE.code(), roleId("PLAYER"));
        Long playerId = insertPlayer(userId, "M02注销戊", PlayerType.PLAYER.code());

        disable(viewer, playerId, "{\"reason\":\"无权限\"}", "disable-1018")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/disable")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"缺少幂等键\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        disable(admin, playerId, "{}", "disable-1019")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        disable(admin, playerId, "{\"reason\":\"   \"}", "disable-1020")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        assertThat(userStatus(userId)).isEqualTo(UserStatus.ACTIVE.code());
        assertThat(countAudit(userId)).isZero();
    }

    private ResultActions disable(Cookie actor, Long playerId, String body, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/players/" + playerId + "/disable")
                .cookie(actor)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Idempotency-Key", idempotencyKey));
    }

    private void assertRegistration(Long matchId, Long playerId, int status, Long cancelledBy) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT status, cancelled_by, cancelled_at FROM registration
                WHERE match_id = ? AND player_id = ? AND is_deleted = 0
                ORDER BY id DESC LIMIT 1
                """, matchId, playerId);
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(status);
        if (cancelledBy == null) {
            assertThat(row.get("cancelled_by")).isNull();
        } else {
            assertThat(((Number) row.get("cancelled_by")).longValue()).isEqualTo(cancelledBy);
            assertThat(row.get("cancelled_at")).isNotNull();
        }
    }

    private int userStatus(Long userId) {
        return jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", Integer.class, userId);
    }

    private int countAudit(Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'USER_DISABLE'
                """, Integer.class, userId);
    }

    private int countOutbox(Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'ACCOUNT_DISABLED' AND aggregate_id = ?
                """, Integer.class, userId);
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

    private Long insertUser(String phone, int status, Long... roleIds) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = userId(phone);
        for (Long roleId : roleIds) {
            jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        createdUserIds.add(userId);
        return userId;
    }

    private Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Long insertPlayer(Long userId, String name, int type) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 1, '[]', 3)
                """, userId, name, type);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long insertMatch(String title, int status, int sequenceNo) {
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, format, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status, capacity,
                    guest_capacity, waitlist_capacity, include_guests, season_match_no, total_match_no,
                    created_by, version)
                VALUES (1, ?, 1, '8人制', '2026-12-01 10:00:00', '2026-12-01 12:00:00',
                    '2026-12-01 00:00:00', '2026-12-01 10:00:00', '测试场地', '2026', '橙队', '兰队',
                    ?, 30, 4, 2, 1, ?, ?, 1, 0)
                """, title, status, sequenceNo, sequenceNo);
        Long matchId = jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
        createdMatchIds.add(matchId);
        return matchId;
    }

    private void insertRegistration(
            Long matchId, Long playerId, int type, int status, String registeredAt) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, ?)
                """, matchId, playerId, type, status, registeredAt);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)", code, code);
        Long roleId = roleId(code);
        jdbc.update("""
                INSERT INTO role_permission (role_id, permission_id)
                SELECT ?, id FROM permission WHERE code = ?
                """, roleId, permissionCode);
        return roleId;
    }

    private Cookie adminLogin(String phone) throws Exception {
        insertUser(phone, UserStatus.ACTIVE.code(), roleId("ADMIN"));
        return login(phone, "Admin123!", "/api/v1/b/auth/login", "YHFC_B");
    }

    private Cookie login(String phone, String password, String path, String cookieName) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult result = mockMvc.perform(post(path)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, cookieName);
    }
}
