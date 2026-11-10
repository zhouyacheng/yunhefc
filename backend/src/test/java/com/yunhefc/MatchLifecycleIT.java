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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MatchLifecycleIT extends IntegrationTestSupport {

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
    void postponeFromEveryAllowedStatusRecomputesTimesAndKeepsRegistrations() throws Exception {
        Cookie admin = bAdminLogin("13900000801");
        Long formal = insertPlayer("13900000811", "M03延期正式");

        for (int status : List.of(0, 1, 2, 3)) {
            Long matchId = createMatch(admin, "M03延期状态" + status, "2026-12-10T20:00:00");
            setStatus(matchId, status);
            insertRegistration(matchId, formal, 1, 0);

            MvcResult result = postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value(5))
                    .andExpect(jsonPath("$.data.statusLabel").value("POSTPONED"))
                    .andExpect(jsonPath("$.data.manualStatus").value(true))
                    .andExpect(jsonPath("$.data.version").value(1))
                    .andExpect(jsonPath("$.data.confirmedCount").value(1))
                    .andReturn();

            assertThat(Instant.parse(json(result, "$.data.startAt")))
                    .isEqualTo(local("2026-12-20T19:00:00"));
            assertThat(Instant.parse(json(result, "$.data.endAt")))
                    .isEqualTo(local("2026-12-20T22:00:00"));
            assertThat(Instant.parse(json(result, "$.data.signupDeadline")))
                    .isEqualTo(local("2026-12-20T00:00:00"));
            assertThat(Instant.parse(json(result, "$.data.cancelLock")))
                    .isEqualTo(local("2026-12-20T10:00:00"));
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM registration WHERE match_id = ?", Integer.class, matchId)).isZero();
        }

        assertThat(createdMatchIds.stream()
                .mapToInt(matchId -> auditCount(matchId, "MATCH_POSTPONE"))
                .sum()).isEqualTo(4);
        Long first = createdMatchIds.get(0);
        assertThat(auditStatus(first, "MATCH_POSTPONE", true)).isZero();
        assertThat(auditStatus(first, "MATCH_POSTPONE", false)).isEqualTo(5);
    }

    @Test
    void postponeRejectsIllegalStatusVersionAndTimes() throws Exception {
        Cookie admin = bAdminLogin("13900000802");

        for (int status : List.of(4, 5, 6)) {
            Long matchId = createMatch(admin, "M03延期非法状态" + status, "2026-12-10T20:00:00");
            setStatus(matchId, status);
            postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }

        Long matchId = createMatch(admin, "M03延期校验", "2026-12-10T20:00:00");
        postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 7)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));
        postpone(admin, matchId, "2026-12-20T09:00:00", "2026-12-20T11:00:00", 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T20:00:00", 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        mockMvc.perform(post("/api/v1/b/matches/999999999/postpone")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startAt":"%s","endAt":"%s","version":0}
                                """.formatted(local("2026-12-20T19:00:00"), local("2026-12-20T22:00:00"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, matchId)).isZero();
        assertThat(auditCount(matchId, "MATCH_POSTPONE")).isZero();
    }

    @Test
    void rescheduleOnlyFromPostponedRestoresSignupOpenAndAutoFlow() throws Exception {
        Cookie admin = bAdminLogin("13900000803");
        Long matchId = createMatch(admin, "M03重新排期", "2026-12-10T20:00:00");

        postpone(admin, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isOk());

        reschedule(admin, matchId, "2026-12-25T18:00:00", "2026-12-25T21:00:00", 9)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));
        reschedule(admin, matchId, "2026-12-25T09:00:00", "2026-12-25T12:00:00", 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        MvcResult result = reschedule(admin, matchId, "2026-12-25T18:00:00", "2026-12-25T21:00:00", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.manualStatus").value(false))
                .andExpect(jsonPath("$.data.version").value(2))
                .andReturn();
        assertThat(Instant.parse(json(result, "$.data.startAt")))
                .isEqualTo(local("2026-12-25T18:00:00"));
        assertThat(Instant.parse(json(result, "$.data.signupDeadline")))
                .isEqualTo(local("2026-12-25T00:00:00"));
        assertThat(Instant.parse(json(result, "$.data.cancelLock")))
                .isEqualTo(local("2026-12-25T10:00:00"));
        assertThat(auditStatus(matchId, "MATCH_RESCHEDULE", true)).isEqualTo(5);
        assertThat(auditStatus(matchId, "MATCH_RESCHEDULE", false)).isZero();

        reschedule(admin, matchId, "2026-12-26T18:00:00", "2026-12-26T21:00:00", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long finished = createMatch(admin, "M03重新排期终态", "2026-12-10T20:00:00");
        setStatus(finished, 4);
        reschedule(admin, finished, "2026-12-26T18:00:00", "2026-12-26T21:00:00", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_RESCHEDULE'
                """, Integer.class, matchId)).isEqualTo(1);
    }

    @Test
    void cancelKeepsRegistrationsAndRecordsReasonInAudit() throws Exception {
        Cookie admin = bAdminLogin("13900000804");
        Long formal = insertPlayer("13900000814", "M03取消正式");
        Long matchId = createMatch(admin, "M03取消", "2026-12-10T20:00:00");
        insertRegistration(matchId, formal, 1, 0);

        cancel(admin, matchId, "  场地临时封闭  ", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6))
                .andExpect(jsonPath("$.data.statusLabel").value("CANCELLED"))
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.confirmedCount").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM registration WHERE match_id = ?", Integer.class, matchId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT active FROM registration WHERE match_id = ?", Integer.class, matchId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                           JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')), '$.reason'))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_CANCEL'
                """, String.class, matchId)).isEqualTo("场地临时封闭");

        cancel(admin, matchId, "再次取消", 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long finished = createMatch(admin, "M03取消终态", "2026-12-10T20:00:00");
        setStatus(finished, 4);
        cancel(admin, finished, "终态取消", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long postponed = createMatch(admin, "M03取消已延期", "2026-12-10T20:00:00");
        postpone(admin, postponed, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isOk());
        cancel(admin, postponed, "延期后取消", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6))
                .andExpect(jsonPath("$.data.version").value(2));

        Long other = createMatch(admin, "M03取消校验", "2026-12-10T20:00:00");
        cancel(admin, other, "版本不一致", 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));
        cancel(admin, other, "   ", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/api/v1/b/matches/" + other + "/cancel")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        cancel(admin, other, "a".repeat(201), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM team_match WHERE id = ?", Integer.class, other)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, other)).isZero();
    }

    @Test
    void cStatusEntryRoutesPostponedAndCancelledThroughDedicatedFlows() throws Exception {
        Cookie bAdmin = bAdminLogin("13900000805");
        Cookie cAdmin = cAdminLogin("13900000806");
        Long matchId = createMatch(bAdmin, "M03C端延期取消", "2026-12-10T20:00:00");

        cStatus(cAdmin, matchId, """
                {"status":5,"version":0,"startAt":"%s","endAt":"%s"}
                """.formatted(local("2026-12-20T19:00:00"), local("2026-12-20T22:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(auditCount(matchId, "MATCH_POSTPONE")).isEqualTo(1);

        cStatus(cAdmin, matchId, """
                {"status":6,"version":1,"reason":"C端取消原因"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6))
                .andExpect(jsonPath("$.data.version").value(2));
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(
                           JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')), '$.reason'))
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_CANCEL'
                """, String.class, matchId)).isEqualTo("C端取消原因");

        Long ordinary = createMatch(bAdmin, "M03C端普通状态", "2026-12-10T20:00:00");
        cStatus(cAdmin, ordinary, "{\"status\":2,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));
        assertThat(auditCount(ordinary, "MATCH_STATUS_CHANGE")).isEqualTo(1);

        Long missingTimes = createMatch(bAdmin, "M03C端缺时间", "2026-12-10T20:00:00");
        cStatus(cAdmin, missingTimes, "{\"status\":5,\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Long missingReason = createMatch(bAdmin, "M03C端缺原因", "2026-12-10T20:00:00");
        cStatus(cAdmin, missingReason, "{\"status\":6,\"version\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        cStatus(cAdmin, missingReason, "{\"status\":6,\"version\":0,\"reason\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void cStatusEntryRequiresEditPermissionForPostponed() throws Exception {
        Cookie bAdmin = bAdminLogin("13900000807");
        Long matchId = createMatch(bAdmin, "M03C端延期权限", "2026-12-10T20:00:00");

        Long statusRole = insertRole("M03_C_STATUS_ONLY", "match:status:any");
        insertUser("13900000808", 1, statusRole);
        Cookie cStatusOnly = cLogin("13900000808", "Status123!");

        cStatus(cStatusOnly, matchId, """
                {"status":5,"version":0,"startAt":"%s","endAt":"%s"}
                """.formatted(local("2026-12-20T19:00:00"), local("2026-12-20T22:00:00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        cStatus(cStatusOnly, matchId, "{\"status\":2,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));

        cStatus(cStatusOnly, matchId, "{\"status\":6,\"version\":1,\"reason\":\"仅有状态权限\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6));

        Long editRole = insertRole("M03_C_EDIT_ONLY", "match:edit:any");
        insertUser("13900000809", 1, editRole);
        Cookie cEditOnly = cLogin("13900000809", "Edit123!");
        Long other = createMatch(bAdmin, "M03C端编辑权限", "2026-12-10T20:00:00");
        cStatus(cEditOnly, other, """
                {"status":5,"version":0,"startAt":"%s","endAt":"%s"}
                """.formatted(local("2026-12-20T19:00:00"), local("2026-12-20T22:00:00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void dedicatedEndpointsEnforcePermissionIdempotencyAndTeamScope() throws Exception {
        Cookie admin = bAdminLogin("13900000810");
        Long matchId = createMatch(admin, "M03专用接口权限", "2026-12-10T20:00:00");

        Long viewerRole = insertRole("M03_LIFECYCLE_VIEWER", "match:view:any");
        insertUser("13900000812", 1, viewerRole);
        Cookie viewer = bLogin("13900000812", "Viewer123!");
        postpone(viewer, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        reschedule(viewer, matchId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        cancel(viewer, matchId, "无权限", 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        String scheduleBody = """
                {"startAt":"%s","endAt":"%s","version":0}
                """.formatted(local("2026-12-20T19:00:00"), local("2026-12-20T22:00:00"));
        for (String path : List.of("/postpone", "/reschedule", "/cancel")) {
            String body = "/cancel".equals(path)
                    ? "{\"reason\":\"缺少幂等键\",\"version\":0}" : scheduleBody;
            mockMvc.perform(post("/api/v1/b/matches/" + matchId + path)
                            .cookie(admin)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001));
        }

        String key = UUID.randomUUID().toString();
        String body = "{\"reason\":\"幂等取消\",\"version\":0}";
        for (int replay = 0; replay < 2; replay++) {
            mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/cancel")
                            .cookie(admin)
                            .with(csrf())
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(6))
                    .andExpect(jsonPath("$.data.version").value(1));
        }
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/cancel")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"幂等取消\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));
        assertThat(auditCount(matchId, "MATCH_CANCEL")).isEqualTo(1);

        Long scheduleId = createMatch(admin, "M03延期幂等", "2026-12-10T20:00:00");
        String postponeKey = UUID.randomUUID().toString();
        for (int replay = 0; replay < 2; replay++) {
            mockMvc.perform(post("/api/v1/b/matches/" + scheduleId + "/postpone")
                            .cookie(admin)
                            .with(csrf())
                            .header("Idempotency-Key", postponeKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scheduleBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(5))
                    .andExpect(jsonPath("$.data.version").value(1));
        }
        assertThat(auditCount(scheduleId, "MATCH_POSTPONE")).isEqualTo(1);

        String rescheduleBody = """
                {"startAt":"%s","endAt":"%s","version":1}
                """.formatted(local("2026-12-25T18:00:00"), local("2026-12-25T21:00:00"));
        String rescheduleKey = UUID.randomUUID().toString();
        for (int replay = 0; replay < 2; replay++) {
            mockMvc.perform(post("/api/v1/b/matches/" + scheduleId + "/reschedule")
                            .cookie(admin)
                            .with(csrf())
                            .header("Idempotency-Key", rescheduleKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rescheduleBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(0))
                    .andExpect(jsonPath("$.data.manualStatus").value(false))
                    .andExpect(jsonPath("$.data.version").value(2));
        }
        assertThat(auditCount(scheduleId, "MATCH_RESCHEDULE")).isEqualTo(1);

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M03跨队延期', 1, '2026-12-01 10:00:00', '2026-12-01 12:00:00',
                    '2026-12-01 00:00:00', '2026-12-01 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999999, 999999, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M03跨队延期'", Long.class);
        createdMatchIds.add(crossTeamId);
        postpone(admin, crossTeamId, "2026-12-20T19:00:00", "2026-12-20T22:00:00", 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    private ResultActions cStatus(Cookie cookie, Long matchId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/status")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private void setStatus(Long matchId, int status) {
        jdbc.update("UPDATE team_match SET status = ? WHERE id = ?", status, matchId);
    }

    private int auditCount(Long matchId, String action) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                """, Integer.class, matchId, action);
    }

    private int auditStatus(Long matchId, String action, boolean before) {
        String column = before ? "beforeJson" : "afterJson";
        return jdbc.queryForObject("""
                SELECT JSON_EXTRACT(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.%s')), '$.status')
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                ORDER BY id ASC LIMIT 1
                """.formatted(column), Integer.class, matchId, action);
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

    private ResultActions postpone(Cookie admin, Long matchId, String startAt, String endAt,
                                   int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/postpone")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt":"%s","endAt":"%s","version":%d}
                        """.formatted(local(startAt), local(endAt), version)));
    }

    private ResultActions reschedule(Cookie admin, Long matchId, String startAt, String endAt,
                                     int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/reschedule")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt":"%s","endAt":"%s","version":%d}
                        """.formatted(local(startAt), local(endAt), version)));
    }

    private ResultActions cancel(Cookie admin, Long matchId, String reason, int version)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/cancel")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reason":"%s","version":%d}
                        """.formatted(reason, version)));
    }

    private String json(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
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

    private Cookie bAdminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        return bLogin(phone, "Admin123!");
    }

    private Cookie cAdminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        return cLogin(phone, "Admin123!");
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
}
