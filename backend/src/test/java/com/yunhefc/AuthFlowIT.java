package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.yunhefc.audit.application.AuditEventConsumer;
import com.yunhefc.notification.application.NotificationEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIT extends IntegrationTestSupport {

    @Autowired
    AuditEventConsumer auditEventConsumer;

    @Autowired
    NotificationEventConsumer notificationEventConsumer;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void selfRegistrationLoginAdminAndTeamInviteFlow() throws Exception {
        String phone = "13900000001";
        sendSms(phone, "REGISTER");

        MvcResult registered = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"测试球员","position":2}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.status").value(0))
                .andExpect(jsonPath("$.data.player.type").value(0))
                .andExpect(jsonPath("$.data.permissions").doesNotExist())
                .andReturn();

        Cookie playerCookie = cookie(registered, "YHFC_C");
        mockMvc.perform(get("/api/v1/c/me").cookie(playerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phoneMasked").value("139****0001"))
                .andExpect(jsonPath("$.data.user.name").value("测试球员"))
                .andExpect(jsonPath("$.data.permissions[?(@ == 'registration:create:own')]").exists());

        MvcResult login = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        assertThat((String) JsonPath.read(login.getResponse().getContentAsString(), "$.data.csrfToken")).isNotBlank();
        Cookie loginCookie = cookie(login, "YHFC_C");
        mockMvc.perform(get("/api/v1/c/me").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phoneMasked").value("139****0001"))
                .andExpect(jsonPath("$.data.user.name").value("测试球员"));

        long adminId = createAdmin("13900000000");
        MvcResult adminLogin = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000000","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Cookie adminCookie = cookie(adminLogin, "YHFC_B");
        assertThat(adminId).isPositive();

        mockMvc.perform(get("/api/v1/b/me").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@ == 'role:manage:any')]").exists());

        mockMvc.perform(get("/api/v1/b/roles").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3));

        Long registeredUserId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long playerRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'PLAYER' AND team_id = 1", Long.class);
        Long adminRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
        mockMvc.perform(put("/api/v1/b/users/" + registeredUserId + "/roles")
                        .cookie(adminCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "test-role-" + registeredUserId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[" + playerRoleId + "," + adminRoleId + "]}"))
                .andExpect(status().isOk());

        var roleChanged = jdbc.queryForMap(
                "SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json "
                        + "FROM outbox_event WHERE event_type = 'ROLE_CHANGED' ORDER BY id DESC LIMIT 1");
        var roleMessage = new com.yunhefc.infra.outbox.OutboxMessage(
                (String) roleChanged.get("event_id"),
                roleChanged.get("team_id") == null ? null : ((Number) roleChanged.get("team_id")).longValue(),
                (String) roleChanged.get("aggregate_type"),
                ((Number) roleChanged.get("aggregate_id")).longValue(),
                (String) roleChanged.get("event_type"),
                (String) roleChanged.get("payload_json"));
        String roleJson = objectMapper.writeValueAsString(roleMessage);
        notificationEventConsumer.apply(roleJson, "yunhefc.notification.events", 0, 1L);
        notificationEventConsumer.apply(roleJson, "yunhefc.notification.events", 0, 1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = ? AND type = 9",
                Integer.class, registeredUserId)).isEqualTo(1);

        var auditEvent = jdbc.queryForMap(
                "SELECT event_id, team_id, aggregate_type, aggregate_id, event_type, payload_json "
                        + "FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' ORDER BY id DESC LIMIT 1");
        var auditMessage = new com.yunhefc.infra.outbox.OutboxMessage(
                (String) auditEvent.get("event_id"),
                auditEvent.get("team_id") == null ? null : ((Number) auditEvent.get("team_id")).longValue(),
                (String) auditEvent.get("aggregate_type"),
                ((Number) auditEvent.get("aggregate_id")).longValue(),
                (String) auditEvent.get("event_type"),
                (String) auditEvent.get("payload_json"));
        String auditJson = objectMapper.writeValueAsString(auditMessage);
        auditEventConsumer.apply(auditJson, "yunhefc.audit.events", 0, 2L);
        auditEventConsumer.apply(auditJson, "yunhefc.audit.events", 0, 2L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'USER_ROLES_UPDATE'",
                Integer.class)).isEqualTo(1);

        MvcResult invite = mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(adminCookie)
                        .with(csrf())
                        .header("Idempotency-Key", "test-invite-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":0,"expiresInDays":7}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").isNotEmpty())
                .andReturn();
        String inviteUrl = JsonPath.read(invite.getResponse().getContentAsString(), "$.data.inviteUrl");
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        String invitedPhone = "13900000002";
        sendSms(invitedPhone, "REGISTER");
        MvcResult invitedRegistration = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"邀请球员","position":3,"inviteToken":"%s"}
                                """.formatted(invitedPhone, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.status").value(1))
                .andExpect(jsonPath("$.data.player.type").value(1))
                .andExpect(jsonPath("$.data.permissions").doesNotExist())
                .andReturn();

        mockMvc.perform(get("/api/v1/c/me").cookie(cookie(invitedRegistration, "YHFC_C")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@ == 'attendance:view:any')]").exists());
    }

    private void sendSms(String phone, String purpose) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"%s"}
                                """.formatted(phone, purpose)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createAdmin(String phone) {
        String hash = passwordEncoder.encode("Admin123!");
        jdbc.update("""
                INSERT INTO app_user (phone, password_hash, status)
                VALUES (?, ?, 1)
                """, phone, hash);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long adminRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, adminRoleId);
        return userId == null ? 0 : userId;
    }
}
