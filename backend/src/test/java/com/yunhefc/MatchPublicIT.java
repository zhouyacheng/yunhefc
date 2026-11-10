package com.yunhefc;

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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MatchPublicIT extends IntegrationTestSupport {

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
    void publicListFiltersScopesSortsAndPaginates() throws Exception {
        Cookie admin = adminLogin("13900000601");
        Long juneFirst = create(admin, matchBody("M03公开六月一", 1, "聚龙足球场", "2099",
                "2099-06-01T20:00:00"));
        Long juneLater = create(admin, matchBody("M03公开六月十五", 2, "滨江球场", "2099",
                "2099-06-15T20:00:00"));
        Long july = create(admin, matchBody("M03公开七月", 1, "聚龙足球场", "2099",
                "2099-07-01T20:00:00"));
        Long previousSeason = create(admin, matchBody("M03公开旧赛季", 1, "聚龙足球场", "2098",
                "2098-06-10T20:00:00"));
        jdbc.update("UPDATE team_match SET status = 4 WHERE id = ?", juneLater);
        jdbc.update("UPDATE team_match SET status = 6 WHERE id = ?", july);

        mockMvc.perform(get("/api/v1/c/matches").param("season", "2099"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items[0].id").value(july))
                .andExpect(jsonPath("$.data.items[1].id").value(juneLater))
                .andExpect(jsonPath("$.data.items[2].id").value(juneFirst))
                .andExpect(jsonPath("$.data.items[0].seasonMatchNo").isNumber())
                .andExpect(jsonPath("$.data.items[0].totalMatchNo").isNumber());

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("month", "2099-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(juneLater))
                .andExpect(jsonPath("$.data.items[1].id").value(juneFirst));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("month", "2099-06").param("sort", "startAt:asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(juneFirst));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("type", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(july))
                .andExpect(jsonPath("$.data.items[1].id").value(juneFirst));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("venue", "聚龙"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("status", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(juneLater));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("scope", "upcoming"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(juneFirst));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("scope", "history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/c/matches")
                        .param("season", "2099").param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(juneLater));

        mockMvc.perform(get("/api/v1/c/matches").param("season", "2098"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(previousSeason));

        expectBadRequest("/api/v1/c/matches", "month", "2099/06");
        expectBadRequest("/api/v1/c/matches", "month", "2099-13");
        expectBadRequest("/api/v1/c/matches", "pageSize", "101");
        expectBadRequest("/api/v1/c/matches", "pageSize", "0");
        expectBadRequest("/api/v1/c/matches", "page", "0");
        expectBadRequest("/api/v1/c/matches", "sort", "name:asc");
        expectBadRequest("/api/v1/c/matches", "scope", "unknown");
        expectBadRequest("/api/v1/c/matches", "type", "9");
        expectBadRequest("/api/v1/c/matches", "status", "9");
        expectBadRequest("/api/v1/c/matches", "season", "26");
    }

    @Test
    void summaryDerivesResultTextAndQueueCounts() throws Exception {
        Cookie admin = adminLogin("13900000602");
        Long win = create(admin, officialBody("M03公开胜", "云河FC", "乙队", 3, 1,
                "2099-08-07T20:00:00"));
        Long loss = create(admin, officialBody("M03公开负", "甲队", "云河FC", 3, 1,
                "2099-08-06T20:00:00"));
        Long draw = create(admin, officialBody("M03公开平", "云河FC", "乙队", 2, 2,
                "2099-08-05T20:00:00"));
        Long internal = create(admin, matchBody("M03公开队内", 1, "聚龙足球场", "2099",
                "2099-08-03T20:00:00"));
        Long noScore = create(admin, matchBody("M03公开无比分", 1, "聚龙足球场", "2099",
                "2099-08-04T20:00:00"));
        jdbc.update("UPDATE team_match SET score_home = NULL, score_away = NULL WHERE id = ?", noScore);

        mockMvc.perform(get("/api/v1/c/matches/" + win))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultText").value("胜"))
                .andExpect(jsonPath("$.data.ourSide").value(0))
                .andExpect(jsonPath("$.data.ourSideLabel").value("HOME"));
        mockMvc.perform(get("/api/v1/c/matches/" + loss))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultText").value("负"))
                .andExpect(jsonPath("$.data.ourSide").value(1));
        mockMvc.perform(get("/api/v1/c/matches/" + draw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultText").value("平"));
        mockMvc.perform(get("/api/v1/c/matches/" + internal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultText").value("0:0"))
                .andExpect(jsonPath("$.data.ourSide").value((Object) null));
        mockMvc.perform(get("/api/v1/c/matches/" + noScore))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resultText").value((Object) null));

        Long confirmedA = insertPlayer("13900000610", "公开正式甲", 901, 1);
        Long confirmedB = insertPlayer("13900000611", "公开正式乙", 902, 1);
        Long guest = insertPlayer("13900000612", "公开外挂", 903, 0);
        Long waitlisted = insertPlayer("13900000613", "公开候补", 904, 1);
        Long cancelled = insertPlayer("13900000614", "公开取消", 905, 1);
        Long removedGuest = insertPlayer("13900000615", "公开移除外挂", 906, 0);
        insertRegistration(win, confirmedA, 1, 0);
        insertRegistration(win, confirmedB, 1, 0);
        insertRegistration(win, guest, 0, 0);
        insertRegistration(win, waitlisted, 1, 1);
        insertRegistration(win, cancelled, 1, 2);
        insertRegistration(win, removedGuest, 0, 3);

        mockMvc.perform(get("/api/v1/c/matches").param("season", "2099").param("type", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(win))
                .andExpect(jsonPath("$.data.items[0].confirmedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].guestCount").value(1))
                .andExpect(jsonPath("$.data.items[0].waitlistCount").value(1));
        mockMvc.perform(get("/api/v1/c/matches/" + win))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCount").value(2))
                .andExpect(jsonPath("$.data.guestCount").value(1))
                .andExpect(jsonPath("$.data.waitlistCount").value(1));
        mockMvc.perform(get("/api/v1/c/matches/" + draw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCount").value(0))
                .andExpect(jsonPath("$.data.guestCount").value(0))
                .andExpect(jsonPath("$.data.waitlistCount").value(0));
    }

    @Test
    void publicDetailReturnsFullInfoAndHandlesMissing() throws Exception {
        Cookie admin = adminLogin("13900000603");
        Long matchId = create(admin, """
                {"title":"M03公开详情","type":0,"startAt":"%s","endAt":"%s","venue":"聚龙足球场",
                 "homeTeamName":"云河FC","awayTeamName":"乙队","competition":"城市联赛","round":5}
                """.formatted(local("2099-09-01T20:00:00"), local("2099-09-01T22:30:00")));

        MvcResult result = mockMvc.perform(get("/api/v1/c/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(matchId))
                .andExpect(jsonPath("$.data.title").value("M03公开详情"))
                .andExpect(jsonPath("$.data.competition").value("城市联赛"))
                .andExpect(jsonPath("$.data.round").value(5))
                .andExpect(jsonPath("$.data.format").value("8人制"))
                .andExpect(jsonPath("$.data.capacity").value(30))
                .andExpect(jsonPath("$.data.guestCapacity").value(4))
                .andExpect(jsonPath("$.data.waitlistCapacity").value(2))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.derivedScore").value((Object) null))
                .andExpect(jsonPath("$.data.myRegistration").value((Object) null))
                .andReturn();
        String json = result.getResponse().getContentAsString();
        Instant startAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(json, "$.data.startAt"));
        assertThatInstant(startAt, "2099-09-01T20:00:00");

        mockMvc.perform(get("/api/v1/c/matches/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M03公开跨队', 1, '2099-09-02 10:00:00', '2099-09-02 12:00:00',
                    '2099-09-02 00:00:00', '2099-09-02 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999998, 999998, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M03公开跨队'", Long.class);
        createdMatchIds.add(crossTeamId);
        mockMvc.perform(get("/api/v1/c/matches/" + crossTeamId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void myRegistrationOnlyVisibleToLoggedInParticipant() throws Exception {
        Cookie admin = adminLogin("13900000604");
        Long matchId = create(admin, officialBody("M03公开本人报名", "云河FC", "乙队", 0, 0, null));

        Long participantUserId = insertActiveUser("13900000620");
        Long participantPlayerId = insertPlayerFor(participantUserId, "公开报名本人", 921, 1);
        Long outsiderUserId = insertActiveUser("13900000621");
        insertPlayerFor(outsiderUserId, "公开旁观", 922, 1);
        insertRegistration(matchId, participantPlayerId, 1, 0);
        Cookie participant = cLogin("13900000620");
        Cookie outsider = cLogin("13900000621");

        mockMvc.perform(get("/api/v1/c/matches/" + matchId).cookie(participant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRegistration.id").isNumber())
                .andExpect(jsonPath("$.data.myRegistration.playerId").value(participantPlayerId))
                .andExpect(jsonPath("$.data.myRegistration.type").value(1))
                .andExpect(jsonPath("$.data.myRegistration.typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.myRegistration.status").value(0))
                .andExpect(jsonPath("$.data.myRegistration.statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.myRegistration.active").value(true))
                .andExpect(jsonPath("$.data.myRegistration.registeredAt").exists());

        mockMvc.perform(get("/api/v1/c/matches/" + matchId).cookie(outsider))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRegistration").value((Object) null));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRegistration").value((Object) null));

        jdbc.update("UPDATE registration SET status = 2 WHERE match_id = ? AND player_id = ?",
                matchId, participantPlayerId);
        mockMvc.perform(get("/api/v1/c/matches/" + matchId).cookie(participant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRegistration").value((Object) null));
    }

    private void expectBadRequest(String path, String name, String value) throws Exception {
        mockMvc.perform(get(path).param(name, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private void assertThatInstant(Instant actual, String localDateTime) {
        org.assertj.core.api.Assertions.assertThat(actual)
                .isEqualTo(LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant());
    }

    private String matchBody(String title, int type, String venue, String season, String startAt) {
        return """
                {"title":"%s","type":%d,"season":"%s","startAt":"%s","endAt":"%s","venue":"%s",
                 "homeTeamName":"橙队","awayTeamName":"兰队"}
                """.formatted(title, type, season, local(startAt),
                local(LocalDateTime.parse(startAt).plusHours(2).toString()), venue);
    }

    private String officialBody(String title, String home, String away,
                                int scoreHome, int scoreAway, String startAt) {
        return """
                {"title":"%s","type":0,"season":"2099","startAt":"%s","endAt":"%s",
                 "venue":"聚龙足球场","homeTeamName":"%s","awayTeamName":"%s",
                 "scoreHome":%d,"scoreAway":%d}
                """.formatted(title, local(startAt == null ? "2099-08-01T20:00:00" : startAt),
                local(startAt == null ? "2099-08-01T22:00:00"
                        : LocalDateTime.parse(startAt).plusHours(2).toString()),
                home, away, scoreHome, scoreAway);
    }

    private Long create(Cookie admin, String body) throws Exception {
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
        return matchId;
    }

    private Long insertPlayer(String phone, String name, int jersey, int type) {
        Long userId = insertActiveUser(phone);
        return insertPlayerFor(userId, name, jersey, type);
    }

    private Long insertPlayerFor(Long userId, String name, int jersey, int type) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, 1, '[]', 3)
                """, userId, name, jersey, type);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long insertActiveUser(String phone) {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Passw0rd!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        createdUserIds.add(userId);
        return userId;
    }

    private void insertRegistration(Long matchId, Long playerId, int type, int status) {
        jdbc.update("INSERT INTO registration (match_id, player_id, type, status) VALUES (?, ?, ?, ?)",
                matchId, playerId, type, status);
    }

    private Cookie cLogin(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Cookie adminLogin(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        createdUserIds.add(userId);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = 'ADMIN'", Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode("Admin123!"), userId);
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

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }
}
