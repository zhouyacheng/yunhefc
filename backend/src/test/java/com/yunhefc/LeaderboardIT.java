package com.yunhefc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 排行榜 STA-03：赛季/生涯窗口、零分母、非法参数与公开访问。
 */
class LeaderboardIT extends IntegrationTestSupport {

    private static final String MALFORMED_SEASON = "202";

    @Test
    void returnsSeasonLeaderboardWithRatesAndZeroFillsMissingRows() throws Exception {
        Long withStats = formalPlayer("13900007001", "排行球员甲", 7, 3);
        Long withoutStats = formalPlayer("13900007002", "排行球员乙", 8, 2);
        insertStats(withStats, season(), 4, 3, 2, 1, 4, 3);

        Long guest = insertPlayer(insertUser("13900007003", 1), "排行外挂", 30, 0, 3);
        insertStats(guest, season(), 7, 5, 4, 1, 4, 4);
        Long pending = insertPlayer(insertUser("13900007004", 0), "排行待审", 31, 1, 3);
        insertStats(pending, season(), 7, 5, 4, 1, 4, 4);
        Long disabled = insertPlayer(insertUser("13900007005", 2), "排行注销", 32, 1, 3);
        insertStats(disabled, season(), 7, 5, 4, 1, 4, 4);

        mockMvc.perform(get("/api/v1/c/team/leaderboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.season").value(season()))
                .andExpect(jsonPath("$.data.scope").value("season"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].playerId").value(withStats))
                .andExpect(jsonPath("$.data.items[0].name").value("排行球员甲"))
                .andExpect(jsonPath("$.data.items[0].jersey").value(7))
                .andExpect(jsonPath("$.data.items[0].position").value(3))
                .andExpect(jsonPath("$.data.items[0].appearances").value(4))
                .andExpect(jsonPath("$.data.items[0].goals").value(3))
                .andExpect(jsonPath("$.data.items[0].assists").value(2))
                .andExpect(jsonPath("$.data.items[0].ownGoals").value(1))
                .andExpect(jsonPath("$.data.items[0].internalAppearances").value(4))
                .andExpect(jsonPath("$.data.items[0].internalWins").value(3))
                .andExpect(jsonPath("$.data.items[0].winRate").value(75.0))
                .andExpect(jsonPath("$.data.items[0].goalsPerMatch").value(0.75))
                .andExpect(jsonPath("$.data.items[0].assistsPerMatch").value(0.5))
                .andExpect(jsonPath("$.data.items[1].playerId").value(withoutStats))
                .andExpect(jsonPath("$.data.items[1].appearances").value(0))
                .andExpect(jsonPath("$.data.items[1].winRate").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].goalsPerMatch").value(0.0))
                .andExpect(jsonPath("$.data.items[1].assistsPerMatch").value(0.0))
                .andExpect(jsonPath("$.data.items[1].history.appearances").value(0))
                .andExpect(jsonPath("$.data.items[1].history.winRate").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].history.goalsPerMatch").value(0.0));
    }

    @Test
    void historyExcludesFutureSeasonsAndZeroFillsMissingPlayers() throws Exception {
        int current = Integer.parseInt(season());
        String future = String.valueOf(current + 1);
        Long noHistory = formalPlayer("13900007032", "排行无历史", 12, 2);
        Long onlyFuture = formalPlayer("13900007033", "排行未来", 13, 2);
        Long onlyCurrent = formalPlayer("13900007034", "排行仅当年", 14, 2);
        Long malformedSeason = formalPlayer("13900007036", "排行非法赛季", 16, 2);
        insertOutOfWindowStats(onlyFuture, future);
        insertStats(onlyCurrent, season(), 4, 3, 2, 1, 4, 3);
        insertOutOfWindowStats(malformedSeason, MALFORMED_SEASON);

        mockMvc.perform(get("/api/v1/c/team/leaderboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].playerId").value(noHistory))
                .andExpect(jsonPath("$.data.items[0].history.appearances").value(0))
                .andExpect(jsonPath("$.data.items[1].playerId").value(onlyFuture))
                .andExpect(jsonPath("$.data.items[1].history.appearances").value(0))
                .andExpect(jsonPath("$.data.items[1].history.goals").value(0))
                .andExpect(jsonPath("$.data.items[1].history.internalWins").value(0))
                .andExpect(jsonPath("$.data.items[1].history.winRate").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].career.appearances").value(0))
                .andExpect(jsonPath("$.data.items[1].career.goals").value(0))
                .andExpect(jsonPath("$.data.items[2].playerId").value(onlyCurrent))
                .andExpect(jsonPath("$.data.items[2].history.appearances").value(0))
                .andExpect(jsonPath("$.data.items[2].history.goals").value(0))
                .andExpect(jsonPath("$.data.items[2].history.assistsPerMatch").value(0.0))
                .andExpect(jsonPath("$.data.items[3].playerId").value(malformedSeason))
                .andExpect(jsonPath("$.data.items[3].history.appearances").value(0))
                .andExpect(jsonPath("$.data.items[3].history.goals").value(0))
                .andExpect(jsonPath("$.data.items[3].career.appearances").value(0))
                .andExpect(jsonPath("$.data.items[3].career.goals").value(0));
    }

    @Test
    void careerScopeExcludesFutureAndMalformedSeasonsAndReturnsNullHistory() throws Exception {
        int current = Integer.parseInt(season());
        Long player = formalPlayer("13900007035", "排行生涯无历史列", 15, 2);
        insertStats(player, season(), 2, 1, 0, 0, 2, 1);
        insertStats(player, String.valueOf(current - 1), 3, 2, 1, 0, 3, 2);
        insertOutOfWindowStats(player, String.valueOf(current + 1));
        insertOutOfWindowStats(player, MALFORMED_SEASON);

        mockMvc.perform(get("/api/v1/c/team/leaderboards").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].appearances").value(5))
                .andExpect(jsonPath("$.data.items[0].goals").value(3))
                .andExpect(jsonPath("$.data.items[0].history").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].career").value(nullValue()));
    }

    @Test
    void seasonScopeIncludesFixedHistoryExcludingCurrentSeason() throws Exception {
        int current = Integer.parseInt(season());
        String previous = String.valueOf(current - 1);
        String beforePrevious = String.valueOf(current - 2);
        Long player = formalPlayer("13900007031", "排行历史", 11, 2);
        insertStats(player, season(), 4, 3, 2, 1, 4, 3);
        insertStats(player, previous, 2, 1, 0, 0, 2, 1);
        insertStats(player, beforePrevious, 3, 2, 1, 0, 3, 2);

        mockMvc.perform(get("/api/v1/c/team/leaderboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].appearances").value(4))
                .andExpect(jsonPath("$.data.items[0].goals").value(3))
                .andExpect(jsonPath("$.data.items[0].history.appearances").value(5))
                .andExpect(jsonPath("$.data.items[0].history.goals").value(3))
                .andExpect(jsonPath("$.data.items[0].history.assists").value(1))
                .andExpect(jsonPath("$.data.items[0].history.ownGoals").value(0))
                .andExpect(jsonPath("$.data.items[0].history.internalAppearances").value(5))
                .andExpect(jsonPath("$.data.items[0].history.internalWins").value(3))
                .andExpect(jsonPath("$.data.items[0].history.winRate").value(60.0))
                .andExpect(jsonPath("$.data.items[0].history.goalsPerMatch").value(0.6))
                .andExpect(jsonPath("$.data.items[0].history.assistsPerMatch").value(0.2))
                .andExpect(jsonPath("$.data.items[0].career.appearances").value(9))
                .andExpect(jsonPath("$.data.items[0].career.goals").value(6))
                .andExpect(jsonPath("$.data.items[0].career.assists").value(3))
                .andExpect(jsonPath("$.data.items[0].career.ownGoals").value(1))
                .andExpect(jsonPath("$.data.items[0].career.internalAppearances").value(9))
                .andExpect(jsonPath("$.data.items[0].career.internalWins").value(6))
                .andExpect(jsonPath("$.data.items[0].career.winRate").value(66.7))
                .andExpect(jsonPath("$.data.items[0].career.goalsPerMatch").value(0.67))
                .andExpect(jsonPath("$.data.items[0].career.assistsPerMatch").value(0.33));

        // 查看历史赛季时主指标跟随所选赛季，历史累计固定为早于当前年的合计。
        mockMvc.perform(get("/api/v1/c/team/leaderboards").param("season", beforePrevious))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].appearances").value(3))
                .andExpect(jsonPath("$.data.items[0].goals").value(2))
                .andExpect(jsonPath("$.data.items[0].winRate").value(66.7))
                .andExpect(jsonPath("$.data.items[0].history.appearances").value(5))
                .andExpect(jsonPath("$.data.items[0].history.goals").value(3))
                .andExpect(jsonPath("$.data.items[0].history.winRate").value(60.0))
                .andExpect(jsonPath("$.data.items[0].career.appearances").value(9))
                .andExpect(jsonPath("$.data.items[0].career.goals").value(6));
    }

    @Test
    void careerScopeSumsSeasonsAndIgnoresSeasonParameter() throws Exception {
        Long player = formalPlayer("13900007011", "排行生涯", 9, 2);
        insertStats(player, season(), 4, 3, 2, 1, 4, 3);
        insertStats(player, "2024", 2, 1, 0, 0, 2, 1);

        mockMvc.perform(get("/api/v1/c/team/leaderboards").param("season", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value("2024"))
                .andExpect(jsonPath("$.data.scope").value("season"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].appearances").value(2))
                .andExpect(jsonPath("$.data.items[0].goals").value(1))
                .andExpect(jsonPath("$.data.items[0].winRate").value(50.0))
                .andExpect(jsonPath("$.data.items[0].goalsPerMatch").value(0.5))
                .andExpect(jsonPath("$.data.items[0].assistsPerMatch").value(0.0));

        mockMvc.perform(get("/api/v1/c/team/leaderboards").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value(nullValue()))
                .andExpect(jsonPath("$.data.scope").value("all"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].appearances").value(6))
                .andExpect(jsonPath("$.data.items[0].goals").value(4))
                .andExpect(jsonPath("$.data.items[0].assists").value(2))
                .andExpect(jsonPath("$.data.items[0].ownGoals").value(1))
                .andExpect(jsonPath("$.data.items[0].internalAppearances").value(6))
                .andExpect(jsonPath("$.data.items[0].internalWins").value(4))
                .andExpect(jsonPath("$.data.items[0].winRate").value(66.7))
                .andExpect(jsonPath("$.data.items[0].goalsPerMatch").value(0.67))
                .andExpect(jsonPath("$.data.items[0].assistsPerMatch").value(0.33));

        // scope=all 时忽略 season，非法值不报错。
        mockMvc.perform(get("/api/v1/c/team/leaderboards")
                        .param("scope", "all").param("season", "abcd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value(nullValue()));
    }

    @Test
    void rejectsInvalidScopeAndSeason() throws Exception {
        mockMvc.perform(get("/api/v1/c/team/leaderboards").param("scope", "career"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/c/team/leaderboards")
                        .param("scope", "season").param("season", "26"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void isPubliclyReadableIncludingPendingAccounts() throws Exception {
        Long player = formalPlayer("13900007021", "排行公开", 10, 2);
        insertStats(player, season(), 1, 0, 0, 0, 1, 0);

        Cookie pendingCookie = pendingLogin("13900007022");

        mockMvc.perform(get("/api/v1/c/team/leaderboards").cookie(pendingCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].playerId").value(player));
    }

    private Cookie pendingLogin(String phone) throws Exception {
        insertUser(phone, 0);
        MvcResult csrf = mockMvc.perform(get("/api/v1/c/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie preLoginCookie = cookie(csrf, "YHFC_C");
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Passw0rd!"), phone);
        MvcResult login = mockMvc.perform(post("/api/v1/c/auth/login")
                        .cookie(preLoginCookie)
                        .header("X-XSRF-TOKEN", csrfToken(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.csrfToken");
        org.assertj.core.api.Assertions.assertThat(csrfToken).isNotBlank();
        return cookie(login, "YHFC_C");
    }

    private String season() {
        return String.valueOf(LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear());
    }

    private Long insertUser(String phone, int status) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Long formalPlayer(String phone, String name, Integer jersey, int position) {
        return insertPlayer(insertUser(phone, 1), name, jersey, 1, position);
    }

    private Long insertPlayer(Long userId, String name, Integer jersey, int type, int position) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position,
                    secondary_positions, region, skill)
                VALUES (1, ?, ?, ?, ?, ?, '[]', '上海', 3.00)
                """, userId, name, jersey, type, position);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    /** 未来/非法赛季的噪声汇总行：用于断言 career/history 窗口排除。 */
    private void insertOutOfWindowStats(Long playerId, String season) {
        insertStats(playerId, season, 9, 9, 9, 9, 9, 9);
    }

    private void insertStats(Long playerId, String season, int appearances, int goals, int assists,
                             int ownGoals, int internalAppearances, int internalWins) {
        jdbc.update("""
                INSERT INTO player_stats_summary (team_id, player_id, season, appearances, goals,
                    assists, own_goals, internal_appearances, internal_wins)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                """, playerId, season, appearances, goals, assists,
                ownGoals, internalAppearances, internalWins);
    }
}
