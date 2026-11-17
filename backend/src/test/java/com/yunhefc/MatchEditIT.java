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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.yunhefc.common.time.ShanghaiTime;

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

        LocalDateTime startAt = ShanghaiTime.parseLenient(json(result, "$.data.startAt"));
        LocalDateTime endAt = ShanghaiTime.parseLenient(json(result, "$.data.endAt"));
        assertThat(startAt).isEqualTo(local("2026-12-12T19:00:00"));
        assertThat(endAt).isEqualTo(local("2026-12-12T22:00:00"));
        assertThat(ShanghaiTime.parseLenient(json(result, "$.data.signupDeadline")))
                .isEqualTo(startAt.toLocalDate().atStartOfDay());
        assertThat(ShanghaiTime.parseLenient(json(result, "$.data.cancelLock")))
                .isEqualTo(startAt.toLocalDate().atTime(1, 0));

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
                {"title":"M03不可改字段后","type":2,"ourSide":1,
                 "scoreHome":3,"scoreAway":2,"version":0}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.type").exists())
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
                """.formatted(local("2026-12-11T00:30:00"), local("2026-12-11T02:30:00")),
                status().isUnprocessableEntity(), 42201);
        Long earlyMatchId = createMatch(admin, "M03早场编辑", "2026-12-10T20:00:00");
        MvcResult early = edit(admin, earlyMatchId, """
                {"startAt":"%s","endAt":"%s","version":0}
                """.formatted(local("2026-12-11T01:30:00"), local("2026-12-11T03:30:00")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(ShanghaiTime.parseLenient(json(early, "$.data.cancelLock")))
                .isEqualTo(local("2026-12-11T01:00:00"));
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
    void terminalAndPostponedMatchesRemainEditableWithoutStatusChange() throws Exception {
        Cookie admin = adminLogin("13900000604");
        Long finished = createMatch(admin, "M03终态已结束", "2026-12-10T20:00:00");
        Long cancelled = createMatch(admin, "M03终态已取消", "2026-12-10T20:00:00");
        Long postponed = createMatch(admin, "M03终态已延期", "2026-12-10T20:00:00");
        changeStatus(admin, finished, 4, 0).andExpect(status().isOk());
        cancel(admin, cancelled, "M03终态编辑", 0).andExpect(status().isOk());
        postpone(admin, postponed, "2026-12-11T19:00:00", "2026-12-11T21:00:00", 0)
                .andExpect(status().isOk());

        Map<Long, Integer> statuses = Map.of(finished, 4, cancelled, 6, postponed, 5);
        for (Map.Entry<Long, Integer> entry : statuses.entrySet()) {
            String title = "M03终态改写" + entry.getKey();
            edit(admin, entry.getKey(), "{\"title\":\"%s\",\"version\":1}".formatted(title))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value(title))
                    .andExpect(jsonPath("$.data.status").value(entry.getValue()))
                    .andExpect(jsonPath("$.data.manualStatus").value(true))
                    .andExpect(jsonPath("$.data.version").value(2));
        }

        MvcResult postponedEdit = edit(admin, postponed, """
                {"startAt":"%s","endAt":"%s","version":2}
                """.formatted(local("2026-12-20T19:00:00"), local("2026-12-20T22:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.manualStatus").value(true))
                .andExpect(jsonPath("$.data.version").value(3))
                .andReturn();
        LocalDateTime startAt = ShanghaiTime.parseLenient(json(postponedEdit, "$.data.startAt"));
        assertThat(ShanghaiTime.parseLenient(json(postponedEdit, "$.data.signupDeadline")))
                .isEqualTo(startAt.toLocalDate().atStartOfDay());
        assertThat(ShanghaiTime.parseLenient(json(postponedEdit, "$.data.cancelLock")))
                .isEqualTo(startAt.toLocalDate().atTime(1, 0));

        // 编辑不额外产生延期/取消通知，状态变更各自只发一次。
        assertThat(countEvents("MATCH_POSTPONED", postponed)).isEqualTo(1);
        assertThat(countEvents("MATCH_CANCELLED", cancelled)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_UPDATE'
                  AND aggregate_id IN (?, ?, ?)
                """, Integer.class, finished, cancelled, postponed)).isEqualTo(4);
    }

    @Test
    void editingCancelledMatchSeasonDoesNotPolluteRedisActiveList() throws Exception {
        Cookie admin = adminLogin("13900000617");
        Long matchId = createMatch(admin, "M03取消改赛季", "2026-12-10T20:00:00");
        int oldSeasonMatchNo = jdbc.queryForObject(
                "SELECT season_match_no FROM team_match WHERE id = ?", Integer.class, matchId);
        cancel(admin, matchId, "改赛季", 0).andExpect(status().isOk());

        MvcResult edited = edit(admin, matchId, "{\"season\":\"2097\",\"version\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(6))
                .andExpect(jsonPath("$.data.version").value(2))
                .andReturn();
        int newSeasonMatchNo = (Integer) com.jayway.jsonpath.JsonPath.read(
                edited.getResponse().getContentAsString(), "$.data.seasonMatchNo");
        assertThat(redisTemplate.opsForList().range("yunhefc:season:2097", 0, -1))
                .doesNotContain(String.valueOf(newSeasonMatchNo));
        assertThat(redisTemplate.opsForList().range("yunhefc:season:2099", 0, -1))
                .doesNotContain(String.valueOf(oldSeasonMatchNo));

        changeStatus(admin, matchId, 0, 2).andExpect(status().isOk());
        assertThat(redisTemplate.opsForList().range("yunhefc:season:2097", 0, -1))
                .contains(String.valueOf(newSeasonMatchNo));
    }

    @Test
    void staleVersionAfterCancelReturnsVersionConflict() throws Exception {
        Cookie admin = adminLogin("13900000618");
        Long matchId = createMatch(admin, "M03取消后旧版本", "2026-12-10T20:00:00");
        cancel(admin, matchId, "并发编辑", 0).andExpect(status().isOk());

        edit(admin, matchId, "{\"title\":\"M03取消后旧版本改\",\"version\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));
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

    @Test
    void seasonChangeRenumbersSeasonMatchNoAndKeepsTotalAndRound() throws Exception {
        Cookie admin = adminLogin("13900000621");
        Long matchId = createMatch(admin, "M03改赛季", "2026-12-10T20:00:00");
        createMatch(admin, "M03目标赛季", "2098", "2026-12-11T20:00:00");

        Map<String, Object> before = jdbc.queryForMap(
                "SELECT season_match_no, total_match_no, round FROM team_match WHERE id = ?", matchId);
        int expectedSeasonNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) FROM team_match
                WHERE team_id = 1 AND season = '2098'
                """, Integer.class) + 1;
        assertThat(before.get("round")).isNotNull();

        edit(admin, matchId, "{\"season\":\"2098\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value("2098"))
                .andExpect(jsonPath("$.data.seasonMatchNo").value(expectedSeasonNo))
                .andExpect(jsonPath("$.data.totalMatchNo")
                        .value(((Number) before.get("total_match_no")).intValue()))
                .andExpect(jsonPath("$.data.round").value(((Number) before.get("round")).intValue()))
                .andExpect(jsonPath("$.data.version").value(1));

        String auditPayload = jdbc.queryForObject("""
                SELECT payload_json FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_UPDATE'
                """, String.class, matchId);
        assertThat(auditPayload).contains("2099").contains("2098");
    }

    @Test
    void seasonChangeMovesNumberBetweenRedisLists() throws Exception {
        Cookie admin = adminLogin("13900000630");
        Long matchId = createMatch(admin, "M03Redis改赛季", "2026-12-10T20:00:00");
        int oldNo = jdbc.queryForObject(
                "SELECT season_match_no FROM team_match WHERE id = ?", Integer.class, matchId);
        Long target = createMatch(admin, "M03Redis目标赛季", "2092", "2026-12-11T20:00:00");
        int targetNo = jdbc.queryForObject(
                "SELECT season_match_no FROM team_match WHERE id = ?", Integer.class, target);

        edit(admin, matchId, "{\"season\":\"2092\",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seasonMatchNo").value(targetNo + 1));

        assertThat(redisTemplate.opsForList().range("yunhefc:season:2099", 0, -1))
                .doesNotContain(String.valueOf(oldNo));
        assertThat(redisTemplate.opsForList().range("yunhefc:season:2092", 0, -1))
                .containsExactlyInAnyOrder(String.valueOf(targetNo), String.valueOf(targetNo + 1));
    }

    @Test
    void sameSeasonUpdateDoesNotRenumber() throws Exception {
        Cookie admin = adminLogin("13900000622");
        Long matchId = createMatch(admin, "M03同赛季", "2026-12-10T20:00:00");
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT season_match_no, total_match_no FROM team_match WHERE id = ?", matchId);

        edit(admin, matchId, "{\"season\":\" 2099 \",\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value("2099"))
                .andExpect(jsonPath("$.data.seasonMatchNo")
                        .value(((Number) before.get("season_match_no")).intValue()))
                .andExpect(jsonPath("$.data.totalMatchNo")
                        .value(((Number) before.get("total_match_no")).intValue()))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void seasonValidationRejectsBlankAndNonFourDigit() throws Exception {
        Cookie admin = adminLogin("13900000623");
        Long matchId = createMatch(admin, "M03赛季校验", "2026-12-10T20:00:00");
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT season, season_match_no FROM team_match WHERE id = ?", matchId);

        for (String body : List.of(
                "{\"season\":null,\"version\":0}",
                "{\"season\":\"\",\"version\":0}",
                "{\"season\":\"   \",\"version\":0}",
                "{\"season\":\"26\",\"version\":0}",
                "{\"season\":\"20266\",\"version\":0}",
                "{\"season\":\"abcd\",\"version\":0}")) {
            edit(admin, matchId, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40001))
                    .andExpect(jsonPath("$.data.details.season").isNotEmpty());
        }

        Map<String, Object> after = jdbc.queryForMap(
                "SELECT season, season_match_no, version FROM team_match WHERE id = ?", matchId);
        assertThat(after.get("season")).isEqualTo("2099");
        assertThat(after.get("season_match_no")).isEqualTo(before.get("season_match_no"));
        assertThat(((Number) after.get("version")).intValue()).isZero();
    }

    @Test
    void seasonChangeCanClearRound() throws Exception {
        Cookie admin = adminLogin("13900000624");
        Long matchId = createMatch(admin, "M03改赛季清场次", "2026-12-10T20:00:00");
        int expectedSeasonNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) FROM team_match
                WHERE team_id = 1 AND season = '2089'
                """, Integer.class) + 1;

        edit(admin, matchId, "{\"season\":\"2089\",\"round\":null,\"version\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value("2089"))
                .andExpect(jsonPath("$.data.seasonMatchNo").value(expectedSeasonNo))
                .andExpect(jsonPath("$.data.round").value((Object) null));
    }

    @Test
    void concurrentSeasonChangesAllocateDistinctNumbers() throws Exception {
        Cookie admin = adminLogin("13900000625");
        Long first = createMatch(admin, "M03并发改赛季A", "2026-12-10T20:00:00");
        Long second = createMatch(admin, "M03并发改赛季B", "2026-12-11T20:00:00");
        Map<String, Object> beforeFirst = jdbc.queryForMap(
                "SELECT total_match_no FROM team_match WHERE id = ?", first);
        Map<String, Object> beforeSecond = jdbc.queryForMap(
                "SELECT total_match_no FROM team_match WHERE id = ?", second);
        int start = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) FROM team_match
                WHERE team_id = 1 AND season = '2088'
                """, Integer.class);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (Long matchId : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    startLatch.await();
                    return edit(admin, matchId, "{\"season\":\"2088\",\"version\":0}")
                            .andExpect(status().isOk())
                            .andReturn();
                }));
            }
            startLatch.countDown();

            List<Integer> numbers = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                String json = future.get(30, TimeUnit.SECONDS).getResponse().getContentAsString();
                numbers.add((Integer) com.jayway.jsonpath.JsonPath.read(json, "$.data.seasonMatchNo"));
            }
            assertThat(numbers).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrder(start + 1, start + 2);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject(
                "SELECT total_match_no FROM team_match WHERE id = ?", Integer.class, first))
                .isEqualTo(((Number) beforeFirst.get("total_match_no")).intValue());
        assertThat(jdbc.queryForObject(
                "SELECT total_match_no FROM team_match WHERE id = ?", Integer.class, second))
                .isEqualTo(((Number) beforeSecond.get("total_match_no")).intValue());
    }

    private Long createMatch(Cookie admin, String title, String startAt) throws Exception {
        return createMatch(admin, title, "2099", startAt);
    }

    private Long createMatch(Cookie admin, String title, String season, String startAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","type":1,"season":"%s","startAt":"%s","endAt":"%s",
                                 "venue":"聚龙足球场","homeTeamName":"橙队","awayTeamName":"兰队"}
                                """.formatted(title, season, local(startAt),
                                ShanghaiTime.parseLenient(startAt).plusHours(2)
                                        .toString())))
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

    private org.springframework.test.web.servlet.ResultActions changeStatus(
            Cookie admin, Long matchId, int status, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/status")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":%d,\"version\":%d}".formatted(status, version)));
    }

    private org.springframework.test.web.servlet.ResultActions postpone(
            Cookie admin, Long matchId, String startAt, String endAt, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/postpone")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startAt":"%s","endAt":"%s","version":%d}
                        """.formatted(local(startAt), local(endAt), version)));
    }

    private org.springframework.test.web.servlet.ResultActions cancel(
            Cookie admin, Long matchId, String reason, int version) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/cancel")
                .cookie(admin)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\",\"version\":%d}".formatted(reason, version)));
    }

    private int countEvents(String eventType, Long matchId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event WHERE event_type = ? AND aggregate_id = ?
                """, Integer.class, eventType, matchId);
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

    private LocalDateTime local(String localDateTime) {
        return ShanghaiTime.parseLenient(localDateTime);
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
