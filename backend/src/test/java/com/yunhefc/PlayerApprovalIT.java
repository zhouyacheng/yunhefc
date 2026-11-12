package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.domain.NotificationType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PlayerApprovalIT extends IntegrationTestSupport {

    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";

    private final List<Long> createdUserIds = new ArrayList<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationEventConsumer notificationEventConsumer;

    @MockitoSpyBean
    private OutboxService outboxService;

    @AfterEach
    void cleanup() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM notification WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void approveActivatesPendingAccountAndKeepsGuestRole() throws Exception {
        Cookie admin = adminLogin("13900000701");
        Long userId = insertUser("13900000711", 0);
        Long playerId = insertPlayer(userId, "M02审核甲", 0, 2);

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "approve-711"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(playerId))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusLabel").value("ACTIVE"))
                .andExpect(jsonPath("$.data.type").value(0))
                .andExpect(jsonPath("$.data.typeLabel").value("GUEST"))
                .andExpect(jsonPath("$.data.wechatBound").value(false))
                .andExpect(jsonPath("$.data.roles[0]").value("GUEST"));

        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", Integer.class, userId))
                .isEqualTo(UserStatus.ACTIVE.code());
        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(PlayerType.GUEST.code());
        assertThat(jdbc.queryForList("""
                SELECT r.code FROM user_role ur
                JOIN app_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND ur.is_deleted = 0
                """, String.class, userId)).containsExactly("GUEST");

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) AS action,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetType')) AS target_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.beforeJson')) AS before_json,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) AS after_json
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, userId);
        assertThat(audit.get("action")).isEqualTo("USER_APPROVE");
        assertThat(audit.get("target_type")).isEqualTo("USER");
        assertThat((String) audit.get("before_json")).contains("\"status\":0");
        assertThat((String) audit.get("after_json")).contains("\"status\":1");

        Map<String, Object> approval = jdbc.queryForMap("""
                SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json
                FROM outbox_event
                WHERE event_type = 'ACCOUNT_APPROVED' AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, userId);
        assertThat(approval.get("aggregate_type")).isEqualTo("USER");

        String message = objectMapper.writeValueAsString(toMessage(approval));
        notificationEventConsumer.apply(message, NOTIFICATION_TOPIC, 0, 31L);
        notificationEventConsumer.apply(message, NOTIFICATION_TOPIC, 0, 31L);

        int approvedType = NotificationType.ACCOUNT_APPROVED.code();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = ?",
                Integer.class, userId, approvedType)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT title FROM notification WHERE user_id = ? AND type = ?",
                String.class, userId, approvedType)).isEqualTo("账号审核通过");
    }

    @Test
    void approvingActiveAccountIsIdempotent() throws Exception {
        Cookie admin = adminLogin("13900000702");
        Long userId = insertUser("13900000712", 1);
        Long playerId = insertPlayer(userId, "M02审核乙", 0, 1);
        int outboxBefore = countOutboxFor(userId);

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "approve-712"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value("GUEST"));

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "approve-712-retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(playerId))
                .andExpect(jsonPath("$.data.status").value(1));

        assertThat(countOutboxFor(userId)).isEqualTo(outboxBefore);
    }

    @Test
    void approvingDisabledAccountIsRejectedWithoutSideEffects() throws Exception {
        Cookie admin = adminLogin("13900000703");
        Long userId = insertUser("13900000713", 2);
        Long playerId = insertPlayer(userId, "M02审核丙", 0, 3);

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "approve-713"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", Integer.class, userId))
                .isEqualTo(UserStatus.DISABLED.code());
        assertThat(countOutboxFor(userId)).isZero();
    }

    @Test
    void approveRequiresPermissionAndIdempotencyKey() throws Exception {
        Cookie admin = adminLogin("13900000704");
        Long editorRole = insertRole("M02_APPROVE_EDITOR", "player:edit:any");
        insertUser("13900000714", 1, editorRole);
        Cookie editor = login("13900000714", "Editor123!");
        Long userId = insertUser("13900000715", 0);
        Long playerId = insertPlayer(userId, "M02审核丁", 0, 0);

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(editor)
                        .with(csrf())
                        .header("Idempotency-Key", "approve-715"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void approveRollsBackWhenOutboxFails() throws Exception {
        Cookie admin = adminLogin("13900000705");
        Long userId = insertUser("13900000716", 0);
        Long playerId = insertPlayer(userId, "M02审核戊", 0, 2);

        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService).append(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/approve")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "approve-716"))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", Integer.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'ACCOUNT_APPROVED' AND aggregate_id = ?",
                Integer.class, userId)).isZero();
    }

    private int countOutboxFor(Long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE aggregate_id = ? AND event_type IN ('AUDIT_RECORDED', 'ACCOUNT_APPROVED')
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

    private Long insertUser(String phone, int status) {
        return insertUser(phone, status, roleId("GUEST"));
    }

    private Long insertUser(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private Long insertPlayer(Long userId, String name, int type, int position) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, '[]', 3)
                """, userId, name, type, position);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
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
        insertUser(phone, 1, roleId("ADMIN"));
        return login(phone, "Admin123!");
    }

    private Cookie login(String phone, String password) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }
}
