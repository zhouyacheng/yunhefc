package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;

class AuditLogIT extends IntegrationTestSupport {

    @Test
    void filtersByActorActionTargetTypeTargetIdAndTimeRange() throws Exception {
        Cookie admin = adminLogin("13900001001");
        Long first = insertAudit(1L, 101L, "张三", "MATCH_CREATE", "MATCH", 501L,
                null, "{\"status\":0}", "audit-a", Instant.parse("2026-01-01T10:00:00Z"));
        Long second = insertAudit(1L, 102L, "李四", "MATCH_UPDATE", "MATCH", 501L,
                "{\"status\":0}", "{\"status\":1}", "audit-b", Instant.parse("2026-01-02T10:00:00Z"));
        Long third = insertAudit(1L, 101L, "张三", "LINEUP_SAVE", "LINEUP", 502L,
                null, "{\"formation\":\"4-4-2\"}", "audit-c", Instant.parse("2026-01-03T10:00:00Z"));
        Long system = insertAudit(1L, null, "系统", "INVITE_EXPIRE", "INVITE", 503L,
                "{\"status\":0}", "{\"status\":2}", "audit-d", Instant.parse("2026-01-04T10:00:00Z"));
        insertAudit(2L, 101L, "张三", "MATCH_CREATE", "MATCH", 900L,
                null, "{\"status\":0}", "audit-other-team", Instant.parse("2026-01-05T10:00:00Z"));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items[0].id").value(system))
                .andExpect(jsonPath("$.data.items[1].id").value(third))
                .andExpect(jsonPath("$.data.items[2].id").value(second))
                .andExpect(jsonPath("$.data.items[3].id").value(first))
                .andExpect(jsonPath("$.data.items[0].actor.id").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].actor.name").value("系统"));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("actor", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(third))
                .andExpect(jsonPath("$.data.items[1].id").value(first));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("actor", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("action", "MATCH_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(second));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("targetType", "MATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(second))
                .andExpect(jsonPath("$.data.items[1].id").value(first));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("targetId", "501"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-03T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(second));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-03T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin)
                        .param("actor", "101")
                        .param("targetType", "MATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(first));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("action", "NOT_EXISTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin)
                        .param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items").isEmpty());

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(4));
    }

    @Test
    void ordersByCreatedAtThenIdDescending() throws Exception {
        Cookie admin = adminLogin("13900001011");
        Instant sameTime = Instant.parse("2026-02-01T00:00:00Z");
        Long lower = insertAudit(1L, 111L, "甲", "ACTION_A", "USER", 1L, null, null, "order-a", sameTime);
        Long higher = insertAudit(1L, 111L, "甲", "ACTION_B", "USER", 2L, null, null, "order-b", sameTime);

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("actor", "111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(higher))
                .andExpect(jsonPath("$.data.items[1].id").value(lower));
    }

    @Test
    void rejectsInvalidFiltersAndBadRequestParams() throws Exception {
        Cookie admin = adminLogin("13900001021");
        insertAudit(1L, 121L, "控制", "MATCH_CREATE", "MATCH", 1L, null, null, "invalid-a",
                Instant.parse("2026-03-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin)
                        .param("from", "2026-03-02T00:00:00Z")
                        .param("to", "2026-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("pageSize", "15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("targetId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("actor", "x".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("from", "not-a-time"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void requiresAuditViewPermission() throws Exception {
        Long viewerRole = insertRole("AUDIT_VIEWER", "match:view:any");
        insertUser("13900001031", 1, viewerRole);
        Cookie viewer = login("13900001031", "Viewer123!");

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/v1/b/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void redactsSnapshotsAndIpInResponseButKeepsRawRows() throws Exception {
        Cookie admin = adminLogin("13900001041");
        String before = """
                {"phone":"13812345678","OpenID":"o-raw","nested":{"PasswordHash":"hash",\
                "region":"杭州","items":[{"token":"raw"},{"TokenHash":42}]},\
                "avatarUrl":"avatar/1/x.jpg","reason":"受伤"}
                """;
        String after = """
                {"phone":"123456","openid":"another","ip":"192.168.10.7"}
                """;
        Long id = insertAudit(1L, 141L, "脱敏", "PLAYER_UPDATE", "PLAYER", 601L,
                before, after, "mask-a", "10.1.2.3", Instant.parse("2026-04-01T00:00:00Z"));

        mockMvc.perform(get("/api/v1/b/audit-logs").cookie(admin).param("action", "PLAYER_UPDATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.phone").value("138****5678"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.OpenID").value("***"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.nested.PasswordHash").value("***"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.nested.region").value("杭州"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.nested.items[0].token").value("***"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.nested.items[1].TokenHash").value("***"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.avatarUrl").value("avatar/1/x.jpg"))
                .andExpect(jsonPath("$.data.items[0].beforeRedacted.reason").value("受伤"))
                .andExpect(jsonPath("$.data.items[0].afterRedacted.phone").value("***"))
                .andExpect(jsonPath("$.data.items[0].afterRedacted.openid").value("***"))
                .andExpect(jsonPath("$.data.items[0].afterRedacted.ip").value("192.168.10.*"))
                .andExpect(jsonPath("$.data.items[0].ipMasked").value("10.1.2.*"))
                .andExpect(jsonPath("$.data.items[0].requestId").value("mask-a"));

        String rawBefore = jdbc.queryForObject(
                "SELECT before_json FROM audit_log WHERE id = ?", String.class, id);
        String rawAfter = jdbc.queryForObject(
                "SELECT after_json FROM audit_log WHERE id = ?", String.class, id);
        String rawIp = jdbc.queryForObject("SELECT ip FROM audit_log WHERE id = ?", String.class, id);
        assertThat(rawBefore).contains("13812345678").contains("o-raw").contains("hash");
        assertThat(rawAfter).contains("192.168.10.7");
        assertThat(rawIp).isEqualTo("10.1.2.3");
    }

    private Long insertAudit(Long teamId, Long actorUserId, String actorName, String action,
                             String targetType, Long targetId, String before, String after,
                             String requestId, Instant createdAt) {
        return insertAudit(teamId, actorUserId, actorName, action, targetType, targetId,
                before, after, requestId, null, createdAt);
    }

    private Long insertAudit(Long teamId, Long actorUserId, String actorName, String action,
                             String targetType, Long targetId, String before, String after,
                             String requestId, String ip, Instant createdAt) {
        jdbc.update("""
                INSERT INTO audit_log (team_id, actor_user_id, actor_name, action, target_type,
                    target_id, before_json, after_json, request_id, ip, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, teamId, actorUserId, actorName, action, targetType, targetId,
                before, after, requestId, ip, Timestamp.from(createdAt));
        return jdbc.queryForObject("SELECT id FROM audit_log WHERE request_id = ?", Long.class, requestId);
    }

    private Long insertUser(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private Long insertRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)", code, code);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
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

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Cookie login(String phone, String password) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }
}
