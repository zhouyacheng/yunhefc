package com.yunhefc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

class TeamPageIT extends IntegrationTestSupport {

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long userId : createdUserIds) {
            jdbc.update("""
                    DELETE s FROM player_stats_summary s
                    JOIN player p ON p.id = s.player_id
                    WHERE p.user_id = ?
                    """, userId);
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void teamPlayersGroupsByPositionAndSortsByAppearancesThenJersey() throws Exception {
        Long forwardHigh = formalPlayer("13900000601", "M02球队前锋甲", 7, 3, 8);
        insertStats(forwardHigh, season(), 8, 4, 2);
        Long forwardLowJersey = formalPlayer("13900000602", "M02球队前锋乙", 9, 3, 4);
        insertStats(forwardLowJersey, season(), 5, 1, 0);
        Long forwardHighJersey = formalPlayer("13900000603", "M02球队前锋丙", 30, 3, 3);
        insertStats(forwardHighJersey, season(), 5, 2, 1);
        Long midfielder = formalPlayer("13900000604", "M02球队中场", 10, 2, 4);
        Long defender = formalPlayer("13900000605", "M02球队后卫", 3, 1, 3);
        Long goalkeeper = formalPlayer("13900000606", "M02球队门将", 1, 0, 5);
        insertStats(goalkeeper, season(), 1, 0, 0);

        Long guestUser = insertUser("13900000607", 1);
        insertPlayer(guestUser, "M02球队外挂", 20, 0, 3, 3);
        Long pendingUser = insertUser("13900000608", 0);
        insertPlayer(pendingUser, "M02球队待审", 21, 1, 2, 3);
        Long disabledUser = insertUser("13900000609", 2);
        insertPlayer(disabledUser, "M02球队注销", 22, 1, 1, 3);
        Long disabledStats = playerIdOf(disabledUser);
        insertStats(disabledStats, season(), 99, 99, 99);

        mockMvc.perform(get("/api/v1/c/team/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].name").value("M02球队前锋甲"))
                .andExpect(jsonPath("$.data.items[0].position").value(3))
                .andExpect(jsonPath("$.data.items[0].positionLabel").value("FORWARD"))
                .andExpect(jsonPath("$.data.items[0].type").value(1))
                .andExpect(jsonPath("$.data.items[0].typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.items[0].jersey").value(7))
                .andExpect(jsonPath("$.data.items[0].appearances").value(8))
                .andExpect(jsonPath("$.data.items[0].secondaryPositions[0]").value(1))
                .andExpect(jsonPath("$.data.items[0].secondaryPositionLabels[0]").value("DEFENDER"))
                .andExpect(jsonPath("$.data.items[1].name").value("M02球队前锋乙"))
                .andExpect(jsonPath("$.data.items[2].name").value("M02球队前锋丙"))
                .andExpect(jsonPath("$.data.items[3].name").value("M02球队中场"))
                .andExpect(jsonPath("$.data.items[3].appearances").value(0))
                .andExpect(jsonPath("$.data.items[4].name").value("M02球队后卫"))
                .andExpect(jsonPath("$.data.items[5].name").value("M02球队门将"));
    }

    @Test
    void teamPlayersToleratesDirtySecondaryPositions() throws Exception {
        Long nullSecondary = insertUser("13900000671", 1);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, 'M02脏副位置甲', 8, 1, 3, 'null', 3)
                """, nullSecondary);
        Long outOfRangeSecondary = insertUser("13900000672", 1);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, 'M02脏副位置乙', 9, 1, 2, '[9,1]', 3)
                """, outOfRangeSecondary);

        mockMvc.perform(get("/api/v1/c/team/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].secondaryPositions.length()").value(0))
                .andExpect(jsonPath("$.data.items[1].secondaryPositions.length()").value(1))
                .andExpect(jsonPath("$.data.items[1].secondaryPositions[0]").value(1));
    }

    @Test
    void teamPlayersLimitCapsEachPositionGroupAndAllIgnoresLimit() throws Exception {
        for (int i = 0; i < 12; i++) {
            Long player = formalPlayer(phone(620 + i), "M02限量前锋" + i, 100 + i, 3, 3);
            insertStats(player, season(), i, 0, 0);
        }
        for (int i = 0; i < 3; i++) {
            formalPlayer(phone(640 + i), "M02限量中场" + i, 200 + i, 2, 3);
        }

        mockMvc.perform(get("/api/v1/c/team/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(15))
                .andExpect(jsonPath("$.data.items.length()").value(13))
                .andExpect(jsonPath("$.data.items[0].name").value("M02限量前锋11"));

        mockMvc.perform(get("/api/v1/c/team/players").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(15))
                .andExpect(jsonPath("$.data.items.length()").value(4));

        mockMvc.perform(get("/api/v1/c/team/players").param("limit", "2").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(15))
                .andExpect(jsonPath("$.data.items.length()").value(15));

        mockMvc.perform(get("/api/v1/c/team/players").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/team/players").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/team/players").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void teamPlayersReturnsEmptyPreviewWhenNoFormalActivePlayers() throws Exception {
        mockMvc.perform(get("/api/v1/c/team/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void teamStatsReturnsCurrentSeasonAndZeroFillsMissingSummaryRows() throws Exception {
        Long withStats = formalPlayer("13900000661", "M02统计甲", 8, 3, 4);
        insertStats(withStats, season(), 6, 3, 2);
        Long withoutStats = formalPlayer("13900000662", "M02统计乙", 9, 2, 3);

        Long guestUser = insertUser("13900000663", 1);
        Long guestPlayer = insertPlayer(guestUser, "M02统计外挂", 30, 0, 3, 3);
        insertStats(guestPlayer, season(), 7, 5, 4);
        Long pendingUser = insertUser("13900000664", 0);
        Long pendingPlayer = insertPlayer(pendingUser, "M02统计待审", 31, 1, 1, 3);
        insertStats(pendingPlayer, season(), 7, 5, 4);
        Long disabledUser = insertUser("13900000665", 2);
        Long disabledPlayer = insertPlayer(disabledUser, "M02统计注销", 32, 1, 0, 3);
        insertStats(disabledPlayer, season(), 7, 5, 4);

        mockMvc.perform(get("/api/v1/c/team/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.season").value(season()))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].playerId").value(withStats))
                .andExpect(jsonPath("$.data.items[0].season").value(season()))
                .andExpect(jsonPath("$.data.items[0].appearances").value(6))
                .andExpect(jsonPath("$.data.items[0].goals").value(3))
                .andExpect(jsonPath("$.data.items[0].assists").value(2))
                .andExpect(jsonPath("$.data.items[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[1].playerId").value(withoutStats))
                .andExpect(jsonPath("$.data.items[1].appearances").value(0))
                .andExpect(jsonPath("$.data.items[1].goals").value(0))
                .andExpect(jsonPath("$.data.items[1].assists").value(0))
                .andExpect(jsonPath("$.data.items[1].updatedAt").value(nullValue()));
    }

    @Test
    void teamStatsAcceptsExplicitSeasonAndRejectsMalformedSeason() throws Exception {
        Long player = formalPlayer("13900000681", "M02统计赛季", 11, 3, 4);
        insertStats(player, season(), 5, 2, 1);
        insertStats(player, "2025", 9, 6, 3);

        mockMvc.perform(get("/api/v1/c/team/stats").param("season", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.season").value("2025"))
                .andExpect(jsonPath("$.data.items[0].season").value("2025"))
                .andExpect(jsonPath("$.data.items[0].appearances").value(9))
                .andExpect(jsonPath("$.data.items[0].goals").value(6))
                .andExpect(jsonPath("$.data.items[0].assists").value(3));

        mockMvc.perform(get("/api/v1/c/team/stats").param("season", "26"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/team/stats").param("season", "abcd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private String season() {
        return String.valueOf(LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear());
    }

    private String phone(int suffix) {
        return "1390000" + suffix;
    }

    private Long insertUser(String phone, int status) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        createdUserIds.add(userId);
        return userId;
    }

    private Long formalPlayer(String phone, String name, Integer jersey, int position, int skill) {
        return insertPlayer(insertUser(phone, 1), name, jersey, 1, position, skill);
    }

    private Long insertPlayer(Long userId, String name, Integer jersey, int type, int position, int skill) {
        String secondary = switch (position) {
            case 0 -> "[1,2]";
            case 1 -> "[2,3]";
            case 2 -> "[1,3]";
            default -> "[1,2]";
        };
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position,
                    secondary_positions, region, avatar_url, skill)
                VALUES (1, ?, ?, ?, ?, ?, ?, '上海', ?, ?)
                """, userId, name, jersey, type, position, secondary, "https://oss.example/" + name + ".png", skill);
        return playerIdOf(userId);
    }

    private Long playerIdOf(Long userId) {
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private void insertStats(Long playerId, String season, int appearances, int goals, int assists) {
        jdbc.update("""
                INSERT INTO player_stats_summary (team_id, player_id, season, appearances, goals, assists)
                VALUES (1, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE appearances = VALUES(appearances), goals = VALUES(goals),
                    assists = VALUES(assists)
                """, playerId, season, appearances, goals, assists);
    }
}
