package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.auth.application.InviteExpiryService;
import com.yunhefc.common.time.ShanghaiTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;

class AuditCoverageIT extends IntegrationTestSupport {

    @Autowired
    private InviteExpiryService inviteExpiryService;

    @Test
    void bindWritesAuditWithMaskedOpenidAndPlayerNameAsActor() throws Exception {
        register("13900001101");
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900001101'", Long.class);
        String playerName = jdbc.queryForObject(
                "SELECT name FROM player WHERE user_id = ?", String.class, userId);

        bindMiniProgram("bind-account", "13900001101");

        Map<String, Object> audit = latestAudit("WECHAT_BIND", userId);
        assertThat(((Number) audit.get("actor_user_id")).longValue()).isEqualTo(userId);
        assertThat(audit.get("actor_name")).isEqualTo(playerName);
        assertThat(audit.get("target_type")).isEqualTo("USER");
        assertThat(((Number) audit.get("target_id")).longValue()).isEqualTo(userId);
        assertThat(audit.get("openid")).isEqualTo("***");
        assertThat((String) audit.get("raw_openid")).doesNotContain("mock-openid-bind-account");
        assertThat(ShanghaiTime.parseLenient((String) audit.get("operate_at")))
                .isBetween(ShanghaiTime.now().minusMinutes(5), ShanghaiTime.now());
    }

    @Test
    void bindWithoutPlayerFallsBackToMaskedPhoneAsActor() throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                "13900001102", passwordEncoder.encode("Passw0rd!"));
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900001102'", Long.class);

        bindMiniProgram("bind-existing-audit", "13900001102");

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

    private void bindMiniProgram(String code, String phone) throws Exception {
        MiniLoginTicket pending = miniLogin(code);
        sendBindSmsCode(phone);
        mockMvc.perform(post("/api/v1/c/auth/mini/bind-phone")
                        .cookie(pending.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindTicket":"%s","phone":"%s","code":"123456"}
                                """.formatted(pending.ticket(), phone)))
                .andExpect(status().isOk());
    }

    private Map<String, Object> latestAudit(String action, Long targetId) {
        return jdbc.queryForMap("""
                SELECT CAST(JSON_EXTRACT(payload_json, '$.actorUserId') AS SIGNED) AS actor_user_id,
                       JSON_TYPE(JSON_EXTRACT(payload_json, '$.actorUserId')) AS actor_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.actorName')) AS actor_name,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.operateAt')) AS operate_at,
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

    private void register(String phone) throws Exception {
        CsrfSession csrf = csrfSession();
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/c/auth/register")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"绑定留痕","position":2}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }
}
