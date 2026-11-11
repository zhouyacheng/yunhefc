package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class MyRegistrationsIT extends IntegrationTestSupport {

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
    void activeScopeIsDefaultAndHistoryIncludesCancelledFinishedAndCancelledMatch() throws Exception {
        Cookie admin = bAdminLogin("13900001101");
        Cookie player = playerLogin("13900001102", "我的报名分组");

        Long openMatch = createMatch(admin, "M04我的报名进行中", "2099-07-01T20:00:00");
        signup(player, openMatch);

        Long cancelledRegistrationMatch = createMatch(admin, "M04我的报名已取消", "2099-07-02T20:00:00");
        signup(player, cancelledRegistrationMatch);
        jdbc.update("UPDATE registration SET status = 2, cancelled_at = NOW(3) WHERE match_id = ?",
                cancelledRegistrationMatch);

        Long finishedMatch = createMatch(admin, "M04我的报名已结束", "2099-07-03T20:00:00");
        signup(player, finishedMatch);
        jdbc.update("UPDATE team_match SET status = 4 WHERE id = ?", finishedMatch);

        Long cancelledMatch = createMatch(admin, "M04我的报名已作废", "2099-07-04T20:00:00");
        signup(player, cancelledMatch);
        jdbc.update("UPDATE team_match SET status = 6 WHERE id = ?", cancelledMatch);

        mockMvc.perform(get("/api/v1/c/me/registrations").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items[0].match.id").value(openMatch))
                .andExpect(jsonPath("$.data.items[0].registration.status").value(0))
                .andExpect(jsonPath("$.data.items[0].registration.statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.items[0].registration.type").value(1))
                .andExpect(jsonPath("$.data.items[0].registration.typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.items[0].registration.player.name").value("我的报名分组"))
                .andExpect(jsonPath("$.data.items[0].match.typeLabel").value("INTERNAL"))
                .andExpect(jsonPath("$.data.items[0].match.statusLabel").value("SIGNUP_OPEN"))
                .andExpect(jsonPath("$.data.items[0].match.confirmedCount").value(1));

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("scope", "active").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].match.id").value(openMatch));

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("scope", "history").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].match.id").value(cancelledMatch))
                .andExpect(jsonPath("$.data.items[0].registration.status").value(0))
                .andExpect(jsonPath("$.data.items[0].match.status").value(6))
                .andExpect(jsonPath("$.data.items[1].match.id").value(finishedMatch))
                .andExpect(jsonPath("$.data.items[1].registration.status").value(0))
                .andExpect(jsonPath("$.data.items[1].match.status").value(4))
                .andExpect(jsonPath("$.data.items[2].match.id").value(cancelledRegistrationMatch))
                .andExpect(jsonPath("$.data.items[2].registration.status").value(2))
                .andExpect(jsonPath("$.data.items[2].registration.statusLabel").value("CANCELLED"));

        // 比赛取消只由 match.status 派生“已作废”，报名状态保持 CONFIRMED。
        assertThat(jdbc.queryForObject(
                "SELECT status FROM registration WHERE match_id = ?", Integer.class, cancelledMatch))
                .isZero();
    }

    @Test
    void onlyLatestRegistrationPerMatchIsReturned() throws Exception {
        Cookie admin = bAdminLogin("13900001103");
        Cookie player = playerLogin("13900001104", "我的报名最新");
        Long matchId = createMatch(admin, "M04我的报名最新记录", "2099-07-05T20:00:00");

        signup(player, matchId);
        Long firstId = registrationId(matchId);
        jdbc.update("UPDATE registration SET status = 2, cancelled_at = NOW(3) WHERE id = ?", firstId);

        signup(player, matchId);
        Long secondId = registrationId(matchId);
        assertThat(secondId).isGreaterThan(firstId);

        mockMvc.perform(get("/api/v1/c/me/registrations").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].registration.id").value(secondId))
                .andExpect(jsonPath("$.data.items[0].registration.status").value(0));

        jdbc.update("UPDATE registration SET status = 2, cancelled_at = NOW(3) WHERE id = ?", secondId);
        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("scope", "history").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].registration.id").value(secondId))
                .andExpect(jsonPath("$.data.items[0].registration.status").value(2));
    }

    @Test
    void paginatesByLatestRegistrationAndValidatesParameters() throws Exception {
        Cookie admin = bAdminLogin("13900001105");
        Cookie player = playerLogin("13900001106", "我的报名分页");
        Long playerPk = playerId("13900001106");
        Long firstMatch = null;
        Long lastMatch = null;
        for (int day = 1; day <= 11; day++) {
            Long matchId = createMatch(admin, "M04我的报名分页" + day,
                    "2099-08-%02dT20:00:00".formatted(day));
            // 分页用例需要 11 条报名记录，直接落库，避免触发报名接口限流
            jdbc.update("""
                    INSERT INTO registration (match_id, player_id, type, status, registered_at)
                    VALUES (?, ?, 1, 0, ?)
                    """, matchId, playerPk, Timestamp.valueOf(LocalDateTime.of(2099, 8, day, 12, 0)));
            firstMatch = firstMatch == null ? matchId : firstMatch;
            lastMatch = matchId;
        }

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("page", "1").param("pageSize", "10").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(10))
                .andExpect(jsonPath("$.data.items[0].match.id").value(lastMatch));

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("page", "2").param("pageSize", "10").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].match.id").value(firstMatch));

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("page", "3").param("pageSize", "10").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("page", String.valueOf(Long.MAX_VALUE))
                        .param("pageSize", "10").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("pageSize", "25").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("page", "0").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("scope", "bogus").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void waitlistedIsActiveAndRemovedIsHistory() throws Exception {
        Cookie admin = bAdminLogin("13900001109");
        Cookie first = playerLogin("13900001110", "我的报名候补一");
        Cookie second = playerLogin("13900001111", "我的报名候补二");
        Long matchId = createMatch(admin, "M04我的报名候补", "2099-07-11T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(first, matchId);
        signup(second, matchId);

        mockMvc.perform(get("/api/v1/c/me/registrations").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].registration.status").value(1))
                .andExpect(jsonPath("$.data.items[0].registration.statusLabel").value("WAITLISTED"));

        jdbc.update("UPDATE registration SET status = 3 WHERE match_id = ? AND status = 1", matchId);

        mockMvc.perform(get("/api/v1/c/me/registrations").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/c/me/registrations")
                        .param("scope", "history").cookie(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].registration.status").value(3))
                .andExpect(jsonPath("$.data.items[0].registration.statusLabel").value("REMOVED"));
    }

    @Test
    void matchRegistrationStatusReturnsNullWhenAbsentAndRejectsUnknownOrCrossTeamMatch()
            throws Exception {
        Cookie admin = bAdminLogin("13900001107");
        Cookie player = playerLogin("13900001108", "我的报名状态");
        Long matchId = createMatch(admin, "M04我的报名状态", "2099-07-09T20:00:00");

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.registration").value(nullValue()))
                .andExpect(jsonPath("$.data.attendance").value(nullValue()));

        signup(player, matchId);
        Long registrationId = registrationId(matchId);
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.id").value(registrationId))
                .andExpect(jsonPath("$.data.registration.playerId").isNumber())
                .andExpect(jsonPath("$.data.registration.type").value(1))
                .andExpect(jsonPath("$.data.registration.typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.registration.status").value(0))
                .andExpect(jsonPath("$.data.registration.statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.registration.active").value(true))
                .andExpect(jsonPath("$.data.attendance").value(nullValue()));

        mockMvc.perform(get("/api/v1/c/matches/999999999/registrations/me").cookie(player))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M04我的报名跨队', 1, '2099-07-10 10:00:00', '2099-07-10 12:00:00',
                    '2099-07-10 00:00:00', '2099-07-10 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999997, 999997, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M04我的报名跨队'", Long.class);
        createdMatchIds.add(crossTeamId);
        mockMvc.perform(get("/api/v1/c/matches/" + crossTeamId + "/registrations/me")
                        .cookie(player))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 即使存在跨队报名脏数据，“我的报名”也只组合本队比赛。
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, 1, 0, NOW(3))
                """, crossTeamId, playerId("13900001108"));
        mockMvc.perform(get("/api/v1/c/me/registrations").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].match.id").value(matchId));

        mockMvc.perform(get("/api/v1/c/me/registrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private ResultActions signup(Cookie cookie, Long matchId) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                        .cookie(cookie)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    private Long registrationId(Long matchId) {
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM registration WHERE match_id = ?", Long.class, matchId);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
    }

    private void setCapacities(Long matchId, int capacity, int guestCapacity, int waitlistCapacity) {
        jdbc.update("""
                UPDATE team_match
                SET capacity = ?, guest_capacity = ?, waitlist_capacity = ?
                WHERE id = ?
                """, capacity, guestCapacity, waitlistCapacity, matchId);
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
                                """.formatted(title, local(startAt), local(
                                LocalDateTime.parse(startAt).plusHours(2).toString()))))
                .andExpect(status().isOk())
                .andReturn();
        Long matchId = JsonPath.<Number>read(
                result.getResponse().getContentAsString(), "$.data.id").longValue();
        createdMatchIds.add(matchId);
        return matchId;
    }

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        Long userId = insertUser(phone, 1, roleId("PLAYER"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, 1, 1, '[]', 3)
                """, userId, name);
        return cLogin(phone, "Player123!");
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private Cookie bAdminLogin(String phone) throws Exception {
        insertUser(phone, 1, roleId("ADMIN"));
        return bLogin(phone, "Admin123!");
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
