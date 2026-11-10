package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MatchAdminIT extends IntegrationTestSupport {

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
    void createAppliesOptionalDefaultsAndDerivesTimes() throws Exception {
        Cookie admin = adminLogin("13900000501");

        MvcResult result = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("橙队VS兰队", 1, "2026-12-01T20:00:00", "2026-12-01T22:00:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.typeLabel").value("INTERNAL"))
                .andExpect(jsonPath("$.data.title").value("橙队VS兰队"))
                .andExpect(jsonPath("$.data.homeTeamName").value("橙队"))
                .andExpect(jsonPath("$.data.awayTeamName").value("兰队"))
                .andExpect(jsonPath("$.data.venue").value("聚龙足球场"))
                .andExpect(jsonPath("$.data.format").value("8人制"))
                .andExpect(jsonPath("$.data.season").value(String.valueOf(ZonedDateTime.now(ZONE).getYear())))
                .andExpect(jsonPath("$.data.ourSide").value((Object) null))
                .andExpect(jsonPath("$.data.ourSideLabel").value((Object) null))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.statusLabel").value("SIGNUP_OPEN"))
                .andExpect(jsonPath("$.data.manualStatus").value(false))
                .andExpect(jsonPath("$.data.includeGuests").value(true))
                .andExpect(jsonPath("$.data.capacity").value(30))
                .andExpect(jsonPath("$.data.guestCapacity").value(4))
                .andExpect(jsonPath("$.data.waitlistCapacity").value(2))
                .andExpect(jsonPath("$.data.scoreHome").value(0))
                .andExpect(jsonPath("$.data.scoreAway").value(0))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andReturn();

        Long matchId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
        createdMatchIds.add(matchId);

        assertThat(matchId).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_CREATE'",
                Integer.class, matchId)).isEqualTo(1);
        String auditPayload = jdbc.queryForObject(
                "SELECT payload_json FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'MATCH_CREATE'",
                String.class, matchId);
        assertThat(auditPayload).contains("橙队VS兰队").contains("聚龙足球场");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_TYPE(JSON_EXTRACT(payload_json, '$.beforeJson')) = 'NULL'",
                Integer.class, matchId)).isEqualTo(1);

        Instant startAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.startAt"));
        Instant endAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.endAt"));
        Instant signupDeadline = Instant.parse(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.signupDeadline"));
        Instant cancelLock = Instant.parse(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.cancelLock"));
        assertThat(startAt).isEqualTo(local("2026-12-01T20:00:00"));
        assertThat(Duration.between(startAt, endAt)).isEqualTo(Duration.ofHours(2));
        assertThat(signupDeadline).isEqualTo(startAt.atZone(ZONE).toLocalDate()
                .atStartOfDay(ZONE).toInstant());
        assertThat(cancelLock).isEqualTo(startAt.atZone(ZONE).toLocalDate()
                .atTime(10, 0).atZone(ZONE).toInstant());
    }

    @Test
    void createValidatesTimeScoreSideAndFields() throws Exception {
        Cookie admin = adminLogin("13900000502");

        createExpecting(admin, createBody("M03时长", 1, "2026-12-01T20:00:00", "2026-12-01T21:00:00"),
                status().isUnprocessableEntity(), 42201);
        createExpecting(admin, createBody("M03时长", 1, "2026-12-01T20:00:00", "2026-12-02T01:00:00"),
                status().isUnprocessableEntity(), 42201);
        createExpecting(admin, createBody("M03开赛", 1, "2026-12-01T09:30:00", "2026-12-01T11:30:00"),
                status().isUnprocessableEntity(), 42201);
        createExpecting(admin, createBody("M03开赛", 1, "2026-12-01T10:00:00", "2026-12-01T12:00:00"),
                status().isUnprocessableEntity(), 42201);
        createExpecting(admin, """
                {"title":"M03比分","type":1,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"橙队","awayTeamName":"兰队","scoreHome":2}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")),
                status().isUnprocessableEntity(), 42201);
        createExpecting(admin, """
                {"title":"M03对外","type":0,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"甲队","awayTeamName":"乙队"}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")),
                status().isUnprocessableEntity(), 42201);
        createExpecting(admin, """
                {"title":"M03类型","type":9,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"橙队","awayTeamName":"兰队"}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")),
                status().isBadRequest(), 40001);
        createExpecting(admin, """
                {"title":"M03名额","type":1,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"橙队","awayTeamName":"兰队","capacity":0}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")),
                status().isBadRequest(), 40001);
        createExpecting(admin, """
                {"title":"M03赛季","type":1,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"橙队","awayTeamName":"兰队","season":"26"}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")),
                status().isBadRequest(), 40001);

        mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        MvcResult explicit = create(admin, """
                {"title":"M03对外显式","type":0,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"甲队","awayTeamName":"乙队","ourSide":1}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")));
        org.assertj.core.api.Assertions.assertThat(
                        (Integer) com.jayway.jsonpath.JsonPath.read(
                                explicit.getResponse().getContentAsString(), "$.data.ourSide"))
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(
                        (String) com.jayway.jsonpath.JsonPath.read(
                                explicit.getResponse().getContentAsString(), "$.data.ourSideLabel"))
                .isEqualTo("AWAY");

        MvcResult inferred = create(admin, """
                {"title":"M03对外推断","type":3,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"云河FC","awayTeamName":"乙队"}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")));
        org.assertj.core.api.Assertions.assertThat(
                        (Integer) com.jayway.jsonpath.JsonPath.read(
                                inferred.getResponse().getContentAsString(), "$.data.ourSide"))
                .isEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(
                        (String) com.jayway.jsonpath.JsonPath.read(
                                inferred.getResponse().getContentAsString(), "$.data.ourSideLabel"))
                .isEqualTo("HOME");

        MvcResult internalWithSide = create(admin, """
                {"title":"M03队内","type":1,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"橙队","awayTeamName":"兰队","ourSide":0}
                """.formatted(local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00")));
        org.assertj.core.api.Assertions.assertThat(
                        (Object) com.jayway.jsonpath.JsonPath.read(
                                internalWithSide.getResponse().getContentAsString(), "$.data.ourSide"))
                .isNull();
    }

    @Test
    void allocatesSequentialMatchNumbersConcurrently() throws Exception {
        Cookie admin = adminLogin("13900000509");
        int startTotal = jdbc.queryForObject(
                "SELECT COALESCE(MAX(total_match_no), 0) FROM team_match WHERE team_id = 1",
                Integer.class);
        int startSeason = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) FROM team_match
                WHERE team_id = 1 AND season = '2097'
                """, Integer.class);

        int parallel = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(parallel);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < parallel; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/b/matches")
                                    .cookie(admin)
                                    .with(csrf())
                                    .header("Idempotency-Key", UUID.randomUUID().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(matchBody("M03并发" + index, 1, "2097",
                                            "2026-12-12T20:" + String.format("%02d", index) + ":00")))
                            .andExpect(status().isOk())
                            .andReturn();
                }));
            }
            start.countDown();

            List<Long> ids = new ArrayList<>();
            List<Integer> totalNumbers = new ArrayList<>();
            List<Integer> seasonNumbers = new ArrayList<>();
            for (java.util.concurrent.Future<MvcResult> future : futures) {
                MvcResult result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                String json = result.getResponse().getContentAsString();
                Long matchId = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.data.id")).longValue();
                ids.add(matchId);
                createdMatchIds.add(matchId);
                totalNumbers.add((Integer) com.jayway.jsonpath.JsonPath.read(json, "$.data.totalMatchNo"));
                seasonNumbers.add((Integer) com.jayway.jsonpath.JsonPath.read(json, "$.data.seasonMatchNo"));
            }

            assertThat(ids).doesNotHaveDuplicates();
            assertThat(totalNumbers).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.IntStream.rangeClosed(startTotal + 1, startTotal + parallel)
                                    .boxed().toList());
            assertThat(seasonNumbers).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(
                            java.util.stream.IntStream.rangeClosed(startSeason + 1, startSeason + parallel)
                                    .boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void creationSnapshotsRegistrationSettings() throws Exception {
        Cookie admin = adminLogin("13900000508");
        String originalPlayers = setting("registration.maxPlayers");
        String originalGuests = setting("registration.maxGuests");
        String originalWaitlist = setting("registration.maxWaitlist");
        try {
            updateSetting("registration.maxPlayers", "33");
            updateSetting("registration.maxGuests", "5");
            updateSetting("registration.maxWaitlist", "3");
            MvcResult first = create(admin, matchBody("M03配置快照甲", 1, "2099", "2026-12-10T20:00:00"));
            Long firstId = id(first);
            mockMvc.perform(get("/api/v1/b/matches/" + firstId).cookie(admin))
                    .andExpect(jsonPath("$.data.capacity").value(33))
                    .andExpect(jsonPath("$.data.guestCapacity").value(5))
                    .andExpect(jsonPath("$.data.waitlistCapacity").value(3));

            updateSetting("registration.maxPlayers", "31");
            updateSetting("registration.maxGuests", "6");
            updateSetting("registration.maxWaitlist", "4");
            MvcResult second = create(admin, matchBody("M03配置快照乙", 1, "2099", "2026-12-11T20:00:00"));
            mockMvc.perform(get("/api/v1/b/matches/" + id(second)).cookie(admin))
                    .andExpect(jsonPath("$.data.capacity").value(31))
                    .andExpect(jsonPath("$.data.guestCapacity").value(6))
                    .andExpect(jsonPath("$.data.waitlistCapacity").value(4));

            mockMvc.perform(get("/api/v1/b/matches/" + firstId).cookie(admin))
                    .andExpect(jsonPath("$.data.capacity").value(33))
                    .andExpect(jsonPath("$.data.guestCapacity").value(5))
                    .andExpect(jsonPath("$.data.waitlistCapacity").value(3));

            MvcResult explicit = create(admin, """
                    {"title":"M03配置覆盖","type":1,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                     "homeTeamName":"橙队","awayTeamName":"兰队",
                     "capacity":12,"guestCapacity":0,"waitlistCapacity":0}
                    """.formatted(local("2026-12-12T20:00:00"), local("2026-12-12T22:00:00")));
            mockMvc.perform(get("/api/v1/b/matches/" + id(explicit)).cookie(admin))
                    .andExpect(jsonPath("$.data.capacity").value(12))
                    .andExpect(jsonPath("$.data.guestCapacity").value(0))
                    .andExpect(jsonPath("$.data.waitlistCapacity").value(0));
        } finally {
            updateSetting("registration.maxPlayers", originalPlayers);
            updateSetting("registration.maxGuests", originalGuests);
            updateSetting("registration.maxWaitlist", originalWaitlist);
        }
    }

    private String setting(String key) {
        return jdbc.queryForObject(
                "SELECT setting_value FROM system_setting WHERE team_id = 1 AND setting_key = ?",
                String.class, key);
    }

    private void updateSetting(String key, String value) {
        jdbc.update("UPDATE system_setting SET setting_value = ? WHERE team_id = 1 AND setting_key = ?",
                value, key);
    }

    @Test
    void permissionsAndIdempotencyAreEnforced() throws Exception {
        Cookie admin = adminLogin("13900000505");
        MvcResult created = create(admin, matchBody("M03幂等", 1, "2099", "2026-12-06T20:00:00"));
        Long matchId = id(created);
        String key = UUID.randomUUID().toString();
        String body = matchBody("M03幂等重放", 1, "2099", "2026-12-07T20:00:00");

        MvcResult first = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        Long replayedId = id(first);
        createdMatchIds.add(replayedId);

        MvcResult replay = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(id(replay)).isEqualTo(replayedId);

        mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin).with(csrf())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody("M03幂等冲突", 1, "2099", "2026-12-08T20:00:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Long viewerRole = insertRole("M03_VIEWER", "match:view:any");
        insertUser("13900000506", 1, viewerRole);
        Cookie viewer = login("13900000506", "Viewer123!");
        mockMvc.perform(get("/api/v1/b/matches/" + matchId).cookie(viewer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/b/matches").cookie(viewer))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(viewer).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Long creatorRole = insertRole("M03_CREATOR", "match:create:any");
        insertUser("13900000507", 1, creatorRole);
        Cookie creator = login("13900000507", "Creator123!");
        MvcResult creatorCreated = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(creator).with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(matchBody("M03仅创建", 1, "2099", "2026-12-09T20:00:00")))
                .andExpect(status().isOk())
                .andReturn();
        createdMatchIds.add(id(creatorCreated));
        mockMvc.perform(get("/api/v1/b/matches").cookie(creator))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/v1/b/matches"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_match WHERE title = 'M03幂等重放' AND team_id = 1",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void detailReturnsVersionAndRejectsMissingOrCrossTeam() throws Exception {
        Cookie admin = adminLogin("13900000504");
        MvcResult created = create(admin, matchBody("M03详情", 1, "2099", "2026-12-05T20:00:00"));
        Long matchId = id(created);

        mockMvc.perform(get("/api/v1/b/matches/" + matchId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(matchId))
                .andExpect(jsonPath("$.data.title").value("M03详情"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.statusLabel").value("SIGNUP_OPEN"))
                .andExpect(jsonPath("$.data.seasonMatchNo").isNumber())
                .andExpect(jsonPath("$.data.totalMatchNo").isNumber());

        mockMvc.perform(get("/api/v1/b/matches/999999999").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M03跨队详情', 1, '2026-12-01 10:00:00', '2026-12-01 12:00:00',
                    '2026-12-01 00:00:00', '2026-12-01 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999999, 999999, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M03跨队详情'", Long.class);
        createdMatchIds.add(crossTeamId);

        mockMvc.perform(get("/api/v1/b/matches/" + crossTeamId).cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void listFiltersScopeSortsAndPaginates() throws Exception {
        Cookie admin = adminLogin("13900000503");

        MvcResult open = create(admin, matchBody("M03列表报名中", 1, "2099", "2026-12-01T20:00:00"));
        MvcResult finished = create(admin, matchBody("M03列表已结束", 2, "2099", "2026-12-02T20:00:00"));
        MvcResult cancelled = create(admin, matchBody("M03列表已取消", 1, "2099", "2026-12-03T20:00:00"));
        create(admin, matchBody("M03列表旧赛季", 1, "2098", "2026-12-04T20:00:00"));
        jdbc.update("UPDATE team_match SET status = 4 WHERE id = ?", id(finished));
        jdbc.update("UPDATE team_match SET status = 6 WHERE id = ?", id(cancelled));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin).param("season", "2099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("M03列表已取消"))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("scope", "upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("M03列表报名中"));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("scope", "history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("status", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("M03列表已结束"));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("type", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("M03列表已结束"));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("sort", "startAt:asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("M03列表报名中"));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pageSize").value(20));

        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("scope", "upcoming")
                        .param("sort", "startAt:asc").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("M03列表报名中"));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("pageSize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(3));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("sort", "name:asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin)
                        .param("season", "2099").param("scope", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin).param("status", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin).param("type", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/matches").cookie(admin).param("season", "26"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private String matchBody(String title, int type, String season, String startAt) {
        return """
                {"title":"%s","type":%d,"season":"%s","startAt":"%s","endAt":"%s",
                 "venue":"聚龙足球场","homeTeamName":"橙队","awayTeamName":"兰队"}
                """.formatted(title, type, season, local(startAt),
                local(java.time.LocalDateTime.parse(startAt).plusHours(2).toString()));
    }

    private String createBody(String title, int type, String startAt, String endAt) {
        return """
                {"title":"%s","type":%d,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"橙队","awayTeamName":"兰队"}
                """.formatted(title, type, local(startAt), local(endAt));
    }

    private Long id(MvcResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private MvcResult create(Cookie admin, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        Long matchId = ((Number) com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
        createdMatchIds.add(matchId);
        return result;
    }

    private void createExpecting(Cookie admin, String body,
                                 org.springframework.test.web.servlet.ResultMatcher expectedStatus,
                                 int code) throws Exception {
        mockMvc.perform(post("/api/v1/b/matches")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expectedStatus)
                .andExpect(jsonPath("$.code").value(code));
    }

    private String local(String localDateTime) {
        return java.time.LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
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
