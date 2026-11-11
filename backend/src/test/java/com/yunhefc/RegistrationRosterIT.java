package com.yunhefc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

class RegistrationRosterIT extends IntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void formalPlayerAndAdminSeeRosterRowsOrderedBySignupTime() throws Exception {
        Cookie admin = bAdminLogin("13900001301");
        Cookie player = playerLogin("13900001302", "名单正式一");
        playerLogin("13900001303", "名单正式二");
        guestLogin("13900001304", "名单外挂一");
        Long matchId = createMatch(admin, "M04名单读取", "2099-09-01T20:00:00");

        Long waitlistedId = insertRegistration(matchId, playerId("13900001302"), 1, 1,
                "2099-09-01 10:00:00", null);
        Long confirmedId = insertRegistration(matchId, playerId("13900001303"), 1, 0,
                "2099-09-01 10:00:01", null);
        Long guestId = insertRegistration(matchId, playerId("13900001304"), 0, 0,
                "2099-09-01 10:00:02", null);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(waitlistedId))
                .andExpect(jsonPath("$.data.items[0].player.id").value(playerId("13900001302")))
                .andExpect(jsonPath("$.data.items[0].player.name").value("名单正式一"))
                .andExpect(jsonPath("$.data.items[0].player.jersey").value(302))
                .andExpect(jsonPath("$.data.items[0].player.position").value(2))
                .andExpect(jsonPath("$.data.items[0].player.positionLabel").value("MIDFIELDER"))
                .andExpect(jsonPath("$.data.items[0].type").value(1))
                .andExpect(jsonPath("$.data.items[0].typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.items[0].status").value(1))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("WAITLISTED"))
                .andExpect(jsonPath("$.data.items[0].registeredAt").exists())
                .andExpect(jsonPath("$.data.items[0].cancelledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].player.phone").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].id").value(confirmedId))
                .andExpect(jsonPath("$.data.items[1].statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.items[2].id").value(guestId))
                .andExpect(jsonPath("$.data.items[2].type").value(0))
                .andExpect(jsonPath("$.data.items[2].typeLabel").value("GUEST"))
                .andExpect(jsonPath("$.data.items[2].player.jersey").value(nullValue()));

        mockMvc.perform(get("/api/v1/b/matches/" + matchId + "/registrations").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(waitlistedId))
                .andExpect(jsonPath("$.data.items[2].id").value(guestId));
    }

    @Test
    void guestCannotReadRoster() throws Exception {
        Cookie admin = bAdminLogin("13900001305");
        Cookie guest = guestLogin("13900001306", "名单外挂二");
        Long matchId = createMatch(admin, "M04名单外挂", "2099-09-02T20:00:00");
        insertRegistration(matchId, playerId("13900001306"), 0, 0,
                "2099-09-02 10:00:00", null);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations").cookie(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void filtersAndPaginationApply() throws Exception {
        Cookie admin = bAdminLogin("13900001307");
        Cookie player = playerLogin("13900001308", "名单分页");
        playerLogin("13900001309", "名单候补");
        playerLogin("13900001310", "名单取消");
        guestLogin("13900001311", "名单外挂三");
        guestLogin("13900001312", "名单外挂四");
        Long matchId = createMatch(admin, "M04名单分页", "2099-09-03T20:00:00");

        insertRegistration(matchId, playerId("13900001308"), 1, 0,
                "2099-09-03 10:00:00", null);
        insertRegistration(matchId, playerId("13900001309"), 1, 1,
                "2099-09-03 10:00:01", null);
        insertRegistration(matchId, playerId("13900001310"), 1, 2,
                "2099-09-03 10:00:02", "2099-09-03 11:00:00");
        insertRegistration(matchId, playerId("13900001311"), 0, 0,
                "2099-09-03 10:00:03", null);
        insertRegistration(matchId, playerId("13900001312"), 0, 2,
                "2099-09-03 10:00:04", "2099-09-03 11:00:00");

        // 已取消的历史报名仍保留在名单中，用于按状态筛选。
        Long earliestId = null;
        for (int i = 0; i < 8; i++) {
            Long id = insertRegistration(matchId, playerId("13900001308"), 1, 2,
                    "2099-09-03 09:00:0" + i, "2099-09-03 09:30:00");
            if (earliestId == null) {
                earliestId = id;
            }
        }

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("type", "0").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].typeLabel").value("GUEST"))
                .andExpect(jsonPath("$.data.items[1].typeLabel").value("GUEST"));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("status", "1").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("WAITLISTED"));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("type", "1").param("status", "2").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(9))
                .andExpect(jsonPath("$.data.items[0].cancelledAt").exists());

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("type", "1").param("page", "2").param("pageSize", "10")
                        .cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(earliestId))
                .andExpect(jsonPath("$.data.total").value(13));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("type", "2").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("status", "9").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("pageSize", "30").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations")
                        .param("page", "0").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void emptyRosterAndCrossTeamAndUnknownMatch() throws Exception {
        Cookie admin = bAdminLogin("13900001313");
        Cookie player = playerLogin("13900001314", "名单空场");
        Long emptyMatchId = createMatch(admin, "M04名单空场", "2099-09-04T20:00:00");

        mockMvc.perform(get("/api/v1/c/matches/" + emptyMatchId + "/registrations").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0));
        mockMvc.perform(get("/api/v1/b/matches/" + emptyMatchId + "/registrations").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M04名单跨队', 1, '2099-09-05 10:00:00', '2099-09-05 12:00:00',
                    '2099-09-05 00:00:00', '2099-09-05 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999996, 999996, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M04名单跨队'", Long.class);

        mockMvc.perform(get("/api/v1/c/matches/" + crossTeamId + "/registrations").cookie(player))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/v1/b/matches/" + crossTeamId + "/registrations").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/v1/c/matches/999999999/registrations").cookie(player))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        mockMvc.perform(get("/api/v1/c/matches/" + emptyMatchId + "/registrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(get("/api/v1/b/matches/" + emptyMatchId + "/registrations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    private Long insertRegistration(Long matchId, Long playerId, int type, int status,
                                    String registeredAt, String cancelledAt) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at, cancelled_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, matchId, playerId, type, status, registeredAt, cancelledAt);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM registration WHERE match_id = ?", Long.class, matchId);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
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
        return JsonPath.<Number>read(
                result.getResponse().getContentAsString(), "$.data.id").longValue();
    }

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 1, Integer.parseInt(phone.substring(phone.length() - 3)));
        return cLogin(phone, "Player123!");
    }

    private Cookie guestLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 0, null);
        return cLogin(phone, "Guest123!");
    }

    private void insertPlayer(String phone, String name, int type, Integer jersey) {
        Long userId = insertUser(phone, 1, roleId(type == 1 ? "PLAYER" : "GUEST"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 2, ?, '[]', 3)
                """, userId, name, type, jersey);
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
