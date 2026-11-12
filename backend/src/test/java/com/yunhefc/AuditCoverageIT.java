package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunhefc.auth.application.InviteExpiryService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

class AuditCoverageIT extends IntegrationTestSupport {

    @Autowired
    private InviteExpiryService inviteExpiryService;

    @Test
    void loggedInWechatBindWritesAuditWithMaskedOpenid() throws Exception {
        Cookie user = register("13900001101");
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900001101'", Long.class);
        String playerName = jdbc.queryForObject(
                "SELECT name FROM player WHERE user_id = ?", String.class, userId);

        MvcResult authorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .cookie(user)
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andReturn();
        String state = state(authorize);

        mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(user)
                        .param("code", "bind-logged-in")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(false));

        Map<String, Object> audit = latestAudit("WECHAT_BIND", userId);
        assertThat(((Number) audit.get("actor_user_id")).longValue()).isEqualTo(userId);
        assertThat(audit.get("actor_name")).isEqualTo(playerName);
        assertThat(audit.get("target_type")).isEqualTo("USER");
        assertThat(((Number) audit.get("target_id")).longValue()).isEqualTo(userId);
        assertThat(audit.get("openid")).isEqualTo("***");
        assertThat((String) audit.get("raw_openid")).doesNotContain("mock-openid-bind-logged-in");
    }

    @Test
    void phoneBindWritesAuditForTheBoundAccount() throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                "13900001102", passwordEncoder.encode("Passw0rd!"));
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900001102'", Long.class);

        MvcResult authorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = cookie(authorize, "YHFC_C");
        String state = state(authorize);
        MvcResult callback = mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(cookie)
                        .param("code", "bind-existing-audit")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(true))
                .andReturn();
        String csrf = JsonPath.read(callback.getResponse().getContentAsString(), "$.data.csrfToken");

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900001102","purpose":"BIND"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/auth/wechat/bind-phone")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900001102","code":"123456"}
                                """))
                .andExpect(status().isOk());

        Map<String, Object> audit = latestAudit("WECHAT_BIND", userId);
        assertThat(((Number) audit.get("actor_user_id")).longValue()).isEqualTo(userId);
        assertThat(audit.get("actor_name")).isEqualTo("139****1102");
        assertThat(audit.get("target_type")).isEqualTo("USER");
        assertThat(((Number) audit.get("target_id")).longValue()).isEqualTo(userId);
        assertThat(audit.get("openid")).isEqualTo("***");
        assertThat((String) audit.get("raw_openid")).doesNotContain("mock-openid-bind-existing-audit");
    }

    @Test
    void inviteExpiryWritesSystemAuditOncePerInvite() {
        jdbc.update("""
                INSERT INTO invite_link (team_id, type, token_hash, inviter_user_id, expires_at)
                VALUES (1, 0, SHA2('expire-audit', 256), 999, DATE_SUB(NOW(3), INTERVAL 1 DAY))
                """);
        Long inviteId = jdbc.queryForObject(
                "SELECT id FROM invite_link WHERE token_hash = SHA2('expire-audit', 256)", Long.class);

        assertThat(inviteExpiryService.expire()).isEqualTo(1);
        inviteExpiryService.expire();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM invite_link WHERE id = ?", Integer.class, inviteId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'INVITE_EXPIRE'
                """, Integer.class)).isEqualTo(1);

        Map<String, Object> audit = latestAudit("INVITE_EXPIRE", inviteId);
        assertThat(audit.get("actor_type")).isEqualTo("NULL");
        assertThat(audit.get("actor_name")).isEqualTo("系统");
        assertThat(audit.get("target_type")).isEqualTo("INVITE");
        assertThat(((Number) audit.get("target_id")).longValue()).isEqualTo(inviteId);
        assertThat(((Number) audit.get("before_status")).intValue()).isZero();
        assertThat(((Number) audit.get("after_status")).intValue()).isEqualTo(2);
    }

    private Map<String, Object> latestAudit(String action, Long targetId) {
        return jdbc.queryForMap("""
                SELECT CAST(JSON_EXTRACT(payload_json, '$.actorUserId') AS SIGNED) AS actor_user_id,
                       JSON_TYPE(JSON_EXTRACT(payload_json, '$.actorUserId')) AS actor_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.actorName')) AS actor_name,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetType')) AS target_type,
                       CAST(JSON_EXTRACT(payload_json, '$.targetId') AS SIGNED) AS target_id,
                       JSON_UNQUOTE(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')), '$.openid')) AS openid,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) AS raw_openid,
                       CAST(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.beforeJson')), '$.status') AS SIGNED) AS before_status,
                       CAST(JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')), '$.status') AS SIGNED) AS after_status
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                  AND JSON_EXTRACT(payload_json, '$.targetId') = ?
                ORDER BY id DESC LIMIT 1
                """, action, targetId);
    }

    private String state(MvcResult authorize) throws Exception {
        String authorizeUrl = JsonPath.read(
                authorize.getResponse().getContentAsString(), "$.data.authorizeUrl");
        return authorizeUrl.substring(authorizeUrl.indexOf("state=") + 6);
    }

    private Cookie register(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"绑定留痕","position":2}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }
}
