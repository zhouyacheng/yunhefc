package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MatchEditIT extends IntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdMatchIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long matchId : createdMatchIds) {
            jdbc.update("DELETE FROM registration WHERE match_id = ?", matchId);
            jdbc.update("DELETE FROM team_match WHERE id = ?", matchId);
        }
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void editUpdatesMutableFieldsRecalculatesTimesAndWritesAudit() throws Exception {
        Cookie admin = adminLogin("13900000601");
        Long matchId = createMatch(admin, "M03编辑原始", "2026-12-10T20:00:00");

        MvcResult result = edit(admin, matchId, """
                {"title":"M03编辑后","startAt":"%s","endAt":"%s","venue":"新场地",
                 "competition":"城市联赛","round":3,"homeTeamName":"甲队","awayTeamName":"乙队",
                 "capacity":20,"guestCapacity":2,"waitlistCapacity":1,"includeGuests":false,"version":0}
                """.formatted(local("2026-12-12T19:00:00"), local("2026-12-12T22:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("M03编辑后"))
                .andExpect(jsonPath("$.data.venue").value("新场地"))
                .andExpect(jsonPath("$.data.competition").value("城市联赛"))
                .andExpect(jsonPath("$.data.round").value(3))
                .andExpect(jsonPath("$.data.homeTeamName").value("甲队"))
                .andExpect(jsonPath("$.data.awayTeamName").value("乙队"))
                .andExpect(jsonPath("$.data.capacity").value(20))
                .andExpect(jsonPath("$.data.guestCapacity").value(2))
                .andExpect(jsonPath("$.data.waitlistCapacity").value(1))
                .andExpect(jsonPath("$.data.includeGuests").value(false))
                .andExpect(jsonPath("$.data.version").value(1))
                .andReturn();

        Instant startAt = Instant.parse(json(result, "$.data.startAt"));
        Instant endAt = Instant.parse(json(result, "$.data.endAt"));
        assertThat(startAt).isEqualTo(local("2026-12-12T19:00:00"));
        assertThat(endAt).isEqualTo(local("2026-12-12T22:00:00"));
        assertThat(Instant.parse(json(result, "$.data.signupDeadline")))
                .isEqualTo(startAt.atZone(ZONE).toLocalDate().atStartOfDay(ZONE).toInstant());
        assertThat(Instant.parse(json(result, "$.data.cancelLock")))
                .isEqualTo(startAt.atZone(ZONE).toLocalDate().atTime(10, 0).atZone(ZONE).toInstant());

        String auditPayload = jdbc.queryForObject("""
                SELECT payload_json FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_UPDATE'
                """, String.class, matchId);
        assertThat(auditPayload)
                .contains("M03编辑原始").contains("M03编辑后")
                .contains("聚龙足球场").contains("新场地");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_UPDATE'
                  AND JSON_TYPE(JSON_EXTRACT(payload_json, '$.beforeJson')) = 'NULL'
                """, Integer.class, matchId)).isZero();
    }

    @Test
    void immutableAndUnknownFieldsAreRejected() throws Exception {
        Cookie admin = adminLogin("13900000602");
        Long matchId = createMatch(admin, "M03不可改字段", "2026-12-10T20:00:00");

        edit(admin, matchId, """
                {"title":"M03不可改字段后","type":2,"season":"2098","ourSide":1,
                 "scoreHome":3,"scoreAway":2,"version":0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.type").exists())
                .andExpect(jsonPath("$.data.details.season").exists())
                .andExpect(jsonPath("$.data.details.ourSide").exists())
                .andExpect(jsonPath("$.data.details.scoreHome").exists())
                .andExpect(jsonPath("$.data.details.scoreAway").exists());

        edit(admin, matchId, "{\"titel\":\"M03拼写错误\",\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.details.titel").exists());

        mockMvc.perform(get("/api/v1/b/matches/" + matchId).cookie(admin))
                .andExpect(jsonPath("$.data.title").value("M03不可改字段"))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.season").value("2099"))
                .andExpect(jsonPath("$.data.ourSide").value((Object) null))
                .andExpect(jsonPath("$.data.scoreHome").value(0))
                .andExpect(jsonPath("$.data.scoreAway").value(0))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    @Test
    void nullableFieldsAreClearedWithExplicitNull() throws Exception {
        Cookie admin = adminLogin("13900000607");
        Long matchId = createMatch(admin, "M03可空字段", "2026-12-10T20:00:00");

        edit(admin, matchId, "{\"competition\":\"城市联赛\",\"round\":3,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.competition").value("城市联赛"))
                .andExpect(jsonPath("$.data.round").value(3));

        edit(admin, matchId, "{\"competition\":null,\"round\":null,\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.competition").value((Object) null))
                .andExpect(jsonPath("$.data.round").value((Object) null));
    }

    @Test
    void timeRulesAndCapacityLowerBoundsAreEnforced() throws Exception {
        Cookie admin = adminLogin("13900000603");
        Long matchId = createMatch(admin, "M03规则校验", "2026-12-10T20:00:00");

        editExpecting(admin, matchId, """
                {"startAt":"%s","endAt":"%s","version":0}
                """.formatted(local("2026-12-11T09:00:00"), local("2026-12-11T11:00:00")),
                status().isUnprocessableEntity(), 42201);
        editExpecting(admin, matchId, """
                {"startAt":"%s","endAt":"%s","version":0}
                """.formatted(local("2026-12-11T20:00:00"), local("2026-12-11T21:00:00")),
                status().isUnprocessableEntity(), 42201);
        // 只改 startAt 时用旧 endAt 校验时长。
        editExpecting(admin, matchId, """
                {"startAt":"%s","version":0}
                """.formatted(local("2026-12-13T20:00:00")),
                status().isUnprocessableEntity(), 42201);

        Long formalA = insertPlayer("13900000611", "M03编辑正式甲");
        Long formalB = insertPlayer("13900000612", "M03编辑正式乙");
        Long formalWaitlist = insertPlayer("13900000613", "M03编辑候补");
        Long guest = insertPlayer("13900000614", "M03编辑外挂");
        insertRegistration(matchId, formalA, 1, 0);
        insertRegistration(matchId, formalB, 1, 0);
        insertRegistration(matchId, formalWaitlist, 1, 1);
        insertRegistration(matchId, guest, 0, 0);

        editExpecting(admin, matchId, "{\"capacity\":1,\"version\":0}",
                status().isUnprocessableEntity(), 42201);
        editExpecting(admin, matchId, "{\"guestCapacity\":0,\"version\":0}",
                status().isUnprocessableEntity(), 42201);

        edit(admin, matchId, "{\"capacity\":2,\"guestCapacity\":1,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(2))
                .andExpect(jsonPath("$.data.guestCapacity").value(1))
                .andExpect(jsonPath("$.data.confirmedCount").value(2))
                .andExpect(jsonPath("$.data.guestCount").value(1))
                .andExpect(jsonPath("$.data.waitlistCount").value(1))
                .andExpect(jsonPath("$.data.version").value(1));

        editExpecting(admin, matchId, "{\"capacity\":0,\"version\":1}",
                status().isBadRequest(), 40001);
        editExpecting(admin, matchId, "{\"title\":\"  \",\"version\":1}",
                status().isBadRequest(), 40001);
        editExpecting(admin, matchId, "{}",
                status().isBadRequest(), 40001);
    }

    @Test
    void terminalAndPostponedMatchesCannotBeEdited() throws Exception {
        Cookie admin = adminLogin("13900000604");
        Long finished = createMatch(admin, "M03终态已结束", "2026-12-10T20:00:00");
        Long cancelled = createMatch(admin, "M03终态已取消", "2026-12-10T20:00:00");
        Long postponed = createMatch(admin, "M03终态已延期", "2026-12-10T20:00:00");
        jdbc.update("UPDATE team_match SET status = 4 WHERE id = ?", finished);
        jdbc.update("UPDATE team_match SET status = 6 WHERE id = ?", cancelled);
        jdbc.update("UPDATE team_match SET status = 5 WHERE id = ?", postponed);

        for (Long matchId : List.of(finished, cancelled, postponed)) {
            editExpecting(admin, matchId, "{\"title\":\"M03终态改写\",\"version\":0}",
                    status().isConflict(), 40905);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_match WHERE title = 'M03终态改写'", Integer.class)).isZero();
    }

    @Test
    void versionMismatchReturnsCurrentVersion() throws Exception {
        Cookie admin = adminLogin("13900000605");
        Long matchId = createMatch(admin, "M03版本", "2026-12-10T20:00:00");

        edit(admin, matchId, "{\"title\":\"M03版本甲\",\"version\":5}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));

        edit(admin, matchId, "{\"title\":\"M03版本甲\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        edit(admin, matchId, "{\"title\":\"M03版本乙\",\"version\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT title FROM team_match WHERE id = ?", String.class, matchId))
                .isEqualTo("M03版本甲");
    }

    @Test
    void permissionsAndMissingMatchAreEnforced() throws Exception {
        Cookie admin = adminLogin("13900000606");
        Long matchId = createMatch(admin, "M03编辑权限", "2026-12-10T20:00:00");

        Long viewerRole = insertRole("M03_EDIT_VIEWER", "match:view:any");
        insertUser("13900000616", 1, viewerRole);
        Cookie viewer = login("13900000616", "Viewer123!");
        editExpecting(viewer, matchId, "{\"title\":\"M03越权\",\"version\":0}",
                status().isForbidden(), 40301);

        Long editorRole = insertRole("M03_EDITOR", "match:edit:any");
        insertUser("13900000617", 1, editorRole);
        Cookie editor = login("13900000617", "Editor123!");
        edit(editor, matchId, "{\"title\":\"M03编辑权限后\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("M03编辑权限后"));

        mockMvc.perform(patch("/api/v1/b/matches/" + matchId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"M03匿名\",\"version\":1}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        editExpecting(admin, 999999999L, "{\"title\":\"M03不存在\",\"version\":0}",
                status().isNotFound(), 40401);

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M03跨队编辑', 1, '2026-12-01 10:00:00', '2026-12-01 12:00:00',
                    '2026-12-01 00:00:00', '2026-12-01 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999999, 999999, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M03跨队编辑'", Long.class);
        createdMatchIds.add(crossTeamId);
        editExpecting(admin, crossTeamId, "{\"title\":\"M03跨队改写\",\"version\":0}",
                status().isNotFound(), 40401);
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
        Long matchId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
        createdMatchIds.add(matchId);
        return matchId;
    }

    private org.springframework.test.web.servlet.ResultActions edit(
            Cookie admin, Long matchId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/b/matches/" + matchId)
                .cookie(admin)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void editExpecting(Cookie admin, Long matchId, String body,
                               org.springframework.test.web.servlet.ResultMatcher expectedStatus,
                               int code) throws Exception {
        edit(admin, matchId, body)
                .andExpect(expectedStatus)
                .andExpect(jsonPath("$.code").value(code));
    }

    private String json(MvcResult result, String path) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }

    private Long insertPlayer(String phone, String name) {
        Long userId = insertUser(phone, 1, roleId("PLAYER"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, 0, 1, '[]', 3)
                """, userId, name);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private void insertRegistration(Long matchId, Long playerId, int type, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, NOW(3))
                """, matchId, playerId, type, status);
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

    private Cookie adminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        return login(phone, "Admin123!");
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
