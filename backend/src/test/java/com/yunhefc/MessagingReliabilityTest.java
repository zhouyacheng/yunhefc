package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.audit.application.AuditEventConsumer;
import com.yunhefc.audit.application.AuditEventHandler;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.infra.outbox.OutboxMessage;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.notification.application.NotificationEventConsumer;
import com.yunhefc.notification.application.NotificationEventHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

class MessagingReliabilityTest extends IntegrationTestSupport {

    private static final String AUDIT_TOPIC = "yunhefc.audit.events";
    private static final String NOTIFICATION_TOPIC = "yunhefc.notification.events";

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuditEventConsumer auditEventConsumer;

    @Autowired
    NotificationEventConsumer notificationEventConsumer;

    @Autowired
    AuditEventHandler auditEventHandler;

    @Autowired
    NotificationEventHandler notificationEventHandler;

    @Autowired
    AuditRecorder auditRecorder;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory;

    @MockitoSpyBean
    OutboxService outboxService;

    @Test
    void inviteRevokeRollsBackWhenAuditOutboxFails() throws Exception {
        Cookie admin = adminLogin("13900000301", null);
        String token = createInvite(admin, "{\"type\":0}");
        Long inviteId = jdbc.queryForObject(
                "SELECT id FROM invite_link WHERE token_hash = SHA2(?, 256)", Long.class, token);

        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService).append(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/b/invites/" + inviteId + "/revoke")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "rollback-invite-revoke"))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject(
                "SELECT status FROM invite_link WHERE id = ?", Integer.class, inviteId)).isZero();
    }

    @Test
    void accountRegistrationRollsBackWhenAuditOutboxFails() throws Exception {
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService).append(any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000302","purpose":"REGISTER"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000302","code":"123456","password":"Passw0rd!","name":"回滚球员","position":2}
                                """))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE phone = '13900000302'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM player WHERE name = '回滚球员'", Integer.class)).isZero();
    }

    @Test
    void roleRenameRollsBackWhenAuditOutboxFails() throws Exception {
        Cookie admin = adminLogin("13900000303", null);
        Long roleId = createRole(admin, "ROLLBACK_ROLE");

        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outboxService).append(any(), any(), any(), any(), any());

        mockMvc.perform(patch("/api/v1/b/roles/" + roleId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"回滚角色\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject(
                "SELECT name FROM app_role WHERE id = ?", String.class, roleId)).isEqualTo("ROLLBACK_ROLE");
    }

    @Test
    void auditAndNotificationConsumersAreIdempotentAndKeepActorSnapshot() throws Exception {
        Cookie admin = adminLogin("13900000304", "审核员");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000304'", Long.class);
        Long playerRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'PLAYER' AND team_id = 1", Long.class);

        mockMvc.perform(put("/api/v1/b/users/" + adminId + "/roles")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "audit-snapshot-roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[" + playerRoleId + "," + adminRoleId() + "]}"))
                .andExpect(status().isOk());

        Map<String, Object> auditRow = jdbc.queryForMap(
                "SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json "
                        + "FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' ORDER BY id DESC LIMIT 1");
        String auditMessage = objectMapper.writeValueAsString(toMessage(auditRow));
        auditEventConsumer.apply(auditMessage, AUDIT_TOPIC, 0, 11L);
        auditEventConsumer.apply(auditMessage, AUDIT_TOPIC, 0, 11L);

        Map<String, Object> log = jdbc.queryForMap(
                "SELECT team_id, actor_user_id, actor_name FROM audit_log "
                        + "WHERE action = 'USER_ROLES_UPDATE' ORDER BY id DESC LIMIT 1");
        assertThat(((Number) log.get("team_id")).longValue()).isEqualTo(1L);
        assertThat(((Number) log.get("actor_user_id")).longValue()).isEqualTo(adminId);
        assertThat(log.get("actor_name")).isEqualTo("审核员");

        Map<String, Object> roleRow = jdbc.queryForMap(
                "SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json "
                        + "FROM outbox_event WHERE event_type = 'ROLE_CHANGED' ORDER BY id DESC LIMIT 1");
        String roleMessage = objectMapper.writeValueAsString(toMessage(roleRow));
        notificationEventConsumer.apply(roleMessage, NOTIFICATION_TOPIC, 0, 12L);
        notificationEventConsumer.apply(roleMessage, NOTIFICATION_TOPIC, 0, 12L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 9",
                Integer.class, adminId)).isEqualTo(1);

        assertThat(jdbc.queryForList(
                "SELECT CONCAT(consumer_group, '@', topic) FROM consumed_event ORDER BY id", String.class))
                .contains("yunhefc-audit@" + AUDIT_TOPIC, "yunhefc-notification@" + NOTIFICATION_TOPIC);
    }

    @Test
    void outboxWrittenInTransactionRollsBackWithFailedBusinessChange() {
        Long before = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            auditRecorder.record(1L, 1L, "审计员", "ROLLBACK_PROBE", "USER", 1L, null, null);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class))
                    .isEqualTo(before + 1);
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class)).isEqualTo(before);
    }

    @Test
    void listenerContainerFactoryUsesConfiguredErrorHandler() {
        assertThat(kafkaListenerContainerFactory.createContainer("probe").getCommonErrorHandler())
                .isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    void consumerDeduplicationRollsBackWithFailedBusinessInsert() throws Exception {
        OutboxMessage auditEvent = new OutboxMessage("audit-rollback-event", 1L, "AUDIT", 42L,
                "AUDIT_RECORDED", "{\"action\":\"X\",\"targetType\":\"T\",\"beforeJson\":\"not-json\"}");
        assertThatThrownBy(() -> auditEventHandler.apply(
                objectMapper.writeValueAsString(auditEvent), AUDIT_TOPIC, 0, 21L))
                .isInstanceOf(IllegalStateException.class);

        OutboxMessage notificationEvent = new OutboxMessage("notification-rollback-event", 1L, "USER", 42L,
                "ROLE_CHANGED", "{}");
        assertThatThrownBy(() -> notificationEventHandler.apply(
                objectMapper.writeValueAsString(notificationEvent), NOTIFICATION_TOPIC, 0, 22L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM consumed_event WHERE event_id IN (?, ?)", Integer.class,
                "audit-rollback-event", "notification-rollback-event")).isZero();
    }

    private Cookie adminLogin(String phone, String playerName) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Admin123!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        if (playerName != null) {
            jdbc.update("""
                    INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                    VALUES (1, ?, ?, 1, 2, '[]', 3)
                    """, userId, playerName);
        }
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

    private String createInvite(Cookie admin, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "reliability-invite-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.data.inviteUrl");
        return url.substring(url.indexOf("token=") + 6);
    }

    private Long createRole(Cookie admin, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/roles")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"%s","permissions":[]}
                                """.formatted(code, code)))
                .andExpect(status().isOk())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private Long adminRoleId() {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
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
}
