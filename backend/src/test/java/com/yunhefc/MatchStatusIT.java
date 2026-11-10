package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MatchStatusIT extends IntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdMatchIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long matchId : createdMatchIds) {
            jdbc.update("DELETE FROM team_match WHERE id = ?", matchId);
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void manualResetSupportsForwardBackwardAndTerminalRestore() throws Exception {
        Cookie admin = bAdminLogin("13900000701");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000701'", Long.class);
        Long matchId = createMatch(admin, "M03状态重置", "2026-12-10T20:00:00");

        changeB(admin, matchId, "{\"status\":3,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.statusLabel").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.statusUpdatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.statusUpdatedBy").value(adminId));

        // 回退。
        changeB(admin, matchId, "{\"status\":0,\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(2));

        changeB(admin, matchId, "{\"status\":4,\"version\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.version").value(3));

        // 终态恢复。
        changeB(admin, matchId, "{\"status\":1,\"version\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(4));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_STATUS_CHANGE'
                """, Integer.class, matchId)).isEqualTo(4);
        assertThat(auditStatus(matchId, true)).isEqualTo(0);
        assertThat(auditStatus(matchId, false)).isEqualTo(3);
    }

    @Test
    void manualStatusDefaultsToTrueAndFalseResumesAutoFlow() throws Exception {
        Cookie admin = bAdminLogin("13900000702");
        Long matchId = createMatch(admin, "M03自动流转开关", "2026-12-10T20:00:00");

        changeB(admin, matchId, "{\"status\":2,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualStatus").value(true));

        changeB(admin, matchId, "{\"status\":2,\"version\":1,\"manualStatus\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.manualStatus").value(false))
                .andExpect(jsonPath("$.data.version").value(2));

        // 再次手动修改时未传 manualStatus，恢复为手动覆盖。
        changeB(admin, matchId, "{\"status\":1,\"version\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(3));
    }

    @Test
    void postponedAndCancelledTargetsAreRejectedOnBackendAndNeedPayloadOnPublicEntrance() throws Exception {
        Cookie admin = bAdminLogin("13900000703");
        Long matchId = createMatch(admin, "M03专用状态拒绝", "2026-12-10T20:00:00");
        Cookie cAdmin = cAdminLogin("13900000704");

        for (int target : List.of(5, 6)) {
            changeB(admin, matchId, "{\"status\":" + target + ",\"version\":0}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
            // C 端延期/取消走专用分支，缺少新时间或原因时按参数校验拒绝。
            changeC(cAdmin, matchId, "{\"status\":" + target + ",\"version\":0}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT manual_status FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_STATUS_CHANGE'
                """, Integer.class, matchId)).isZero();
    }

    @Test
    void versionConflictAndMissingMatchAreReported() throws Exception {
        Cookie admin = bAdminLogin("13900000705");
        Long matchId = createMatch(admin, "M03状态版本", "2026-12-10T20:00:00");

        changeB(admin, matchId, "{\"status\":2,\"version\":9}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));

        changeB(admin, matchId, "{\"status\":2,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        changeB(admin, matchId, "{\"status\":3,\"version\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));

        changeB(admin, 999999999L, "{\"status\":2,\"version\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M03跨队状态', 1, '2026-12-01 10:00:00', '2026-12-01 12:00:00',
                    '2026-12-01 00:00:00', '2026-12-01 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999999, 999999, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M03跨队状态'", Long.class);
        createdMatchIds.add(crossTeamId);
        changeB(admin, crossTeamId, "{\"status\":2,\"version\":0}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void publicEntranceRequiresStatusPermission() throws Exception {
        Cookie admin = bAdminLogin("13900000706");
        Long matchId = createMatch(admin, "M03状态权限", "2026-12-10T20:00:00");
        Cookie cAdmin = cAdminLogin("13900000707");

        changeC(cAdmin, matchId, "{\"status\":3,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(3))
                .andExpect(jsonPath("$.data.version").value(1));

        Long viewerRole = insertRole("M03_STATUS_VIEWER", "match:view:any");
        insertUser("13900000708", 1, viewerRole);
        Cookie cViewer = cLogin("13900000708", "Viewer123!");
        Cookie bViewer = bLogin("13900000708", "Viewer123!");
        changeC(cViewer, matchId, "{\"status\":2,\"version\":1}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        changeB(bViewer, matchId, "{\"status\":2,\"version\":1}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Long statusRole = insertRole("M03_STATUS_EDITOR", "match:status:any");
        insertUser("13900000709", 1, statusRole);
        Cookie cEditor = cLogin("13900000709", "Editor123!");
        changeC(cEditor, matchId, "{\"status\":1,\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/status")
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2,\"version\":2}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void idempotencyKeyIsRequiredAndReplaysFirstResult() throws Exception {
        Cookie admin = bAdminLogin("13900000710");
        Long matchId = createMatch(admin, "M03状态幂等", "2026-12-10T20:00:00");
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/status")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":3,\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        MvcResult first = changeWithKey(admin, "/api/v1/b/matches/" + matchId + "/status", key,
                "{\"status\":3,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn();

        MvcResult replay = changeWithKey(admin, "/api/v1/b/matches/" + matchId + "/status", key,
                "{\"status\":3,\"version\":0}")
                .andExpect(status().isOk())
                .andReturn();
        assertThat(JsonPath.<Integer>read(replay.getResponse().getContentAsString(), "$.data.version"))
                .isEqualTo(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.data.version"));

        changeWithKey(admin, "/api/v1/b/matches/" + matchId + "/status", key,
                "{\"status\":2,\"version\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        // C 端入口同样复用幂等键校验。
        Cookie cAdmin = cLogin("13900000710", "Admin123!");
        String cKey = UUID.randomUUID().toString();
        String cPath = "/api/v1/c/matches/" + matchId + "/status";
        changeWithKey(cAdmin, cPath, cKey, "{\"status\":2,\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));
        changeWithKey(cAdmin, cPath, cKey, "{\"status\":2,\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, matchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_STATUS_CHANGE'
                """, Integer.class, matchId)).isEqualTo(2);
    }

    private int auditStatus(Long matchId, boolean before) {
        String column = before ? "beforeJson" : "afterJson";
        return jdbc.queryForObject("""
                SELECT JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.%s')), '$.status')
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_STATUS_CHANGE'
                ORDER BY id ASC LIMIT 1
                """.formatted(column), Integer.class, matchId);
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
                                LocalDateTime.parse(startAt).plusHours(2)
                                        .atZone(ZONE).toInstant().toString())))
                .andExpect(status().isOk())
                .andReturn();
        Long matchId = JsonPath.<Number>read(
                result.getResponse().getContentAsString(), "$.data.id").longValue();
        createdMatchIds.add(matchId);
        return matchId;
    }

    private ResultActions changeB(Cookie cookie, Long matchId, String body) throws Exception {
        return changeWithKey(cookie, "/api/v1/b/matches/" + matchId + "/status",
                UUID.randomUUID().toString(), body);
    }

    private ResultActions changeC(Cookie cookie, Long matchId, String body) throws Exception {
        return changeWithKey(cookie, "/api/v1/c/matches/" + matchId + "/status",
                UUID.randomUUID().toString(), body);
    }

    private ResultActions changeWithKey(Cookie cookie, String path, String key, String body) throws Exception {
        return mockMvc.perform(post(path)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }

    private Long insertRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)",
                code, code);
        Long roleId = roleId(code);
        jdbc.update("""
                INSERT INTO role_permission (role_id, permission_id)
                SELECT ?, id FROM permission WHERE code = ?
                """, roleId, permissionCode);
        return roleId;
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private Cookie bAdminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        return bLogin(phone, "Admin123!");
    }

    private Cookie cAdminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        return cLogin(phone, "Admin123!");
    }

    private Cookie bLogin(String phone, String password) throws Exception {
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
