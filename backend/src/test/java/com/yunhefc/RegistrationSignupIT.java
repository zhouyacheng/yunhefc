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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class RegistrationSignupIT extends IntegrationTestSupport {

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
    void formalSignupConfirmsWhileCapacityAvailable() throws Exception {
        Cookie admin = bAdminLogin("13900000701");
        Cookie player = playerLogin("13900000702", "正式报名一");
        Long matchId = createMatch(admin, "M04正式报名", "2099-06-01T20:00:00");
        setCapacities(matchId, 2, 1, 1);

        MvcResult result = signup(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.registration.id").isNumber())
                .andExpect(jsonPath("$.data.registration.type").value(1))
                .andExpect(jsonPath("$.data.registration.typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.registration.status").value(0))
                .andExpect(jsonPath("$.data.registration.statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.registration.active").value(true))
                .andExpect(jsonPath("$.data.registration.registeredAt").isString())
                .andReturn();

        assertThat(jdbc.queryForObject(
                "SELECT player_id FROM registration WHERE id = ?", Long.class,
                JsonPath.<Number>read(result.getResponse().getContentAsString(),
                        "$.data.registration.id").longValue()))
                .isEqualTo(playerId("13900000702"));
    }

    @Test
    void formalSignupWaitlistsWhenCapacityFullAndRejectsWhenWaitlistFull() throws Exception {
        Cookie admin = bAdminLogin("13900000703");
        Long matchId = createMatch(admin, "M04候补", "2099-06-02T20:00:00");
        setCapacities(matchId, 1, 4, 1);

        Cookie first = playerLogin("13900000704", "正式候补一");
        Cookie second = playerLogin("13900000705", "正式候补二");
        Cookie third = playerLogin("13900000706", "正式候补三");

        signup(first, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(0));
        signup(second, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(1))
                .andExpect(jsonPath("$.data.registration.statusLabel").value("WAITLISTED"))
                .andExpect(jsonPath("$.data.registration.active").value(true));
        signup(third, matchId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isEqualTo(1);
    }

    @Test
    void guestSignupConfirmsOrIsRejectedWithoutWaitlist() throws Exception {
        Cookie admin = bAdminLogin("13900000707");
        Long matchId = createMatch(admin, "M04外挂", "2099-06-03T20:00:00");
        setCapacities(matchId, 1, 0, 2);

        Cookie guest = guestLogin("13900000708", "外挂一");
        signup(guest, matchId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        setCapacities(matchId, 1, 1, 2);
        signup(guest, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.type").value(0))
                .andExpect(jsonPath("$.data.registration.typeLabel").value("GUEST"))
                .andExpect(jsonPath("$.data.registration.status").value(0));

        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isZero();
    }

    @Test
    void duplicateActiveSignupIsRejectedButCancelledHistoryAllowsReapply() throws Exception {
        Cookie admin = bAdminLogin("13900000709");
        Cookie player = playerLogin("13900000710", "重复报名");
        Long matchId = createMatch(admin, "M04重复报名", "2099-06-04T20:00:00");
        setCapacities(matchId, 2, 1, 1);

        signup(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk());
        signup(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));

        jdbc.update("UPDATE registration SET status = 2, cancelled_at = NOW(3) WHERE match_id = ?",
                matchId);
        signup(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(0));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(2);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);

        jdbc.update("UPDATE registration SET status = 3 WHERE match_id = ? AND status = 0", matchId);
        signup(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(0));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(3);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
    }

    @Test
    void signupRequiresOpenMatchDeadlineAndPlayerProfile() throws Exception {
        Cookie admin = bAdminLogin("13900000711");
        Cookie player = playerLogin("13900000712", "资格校验");
        for (int matchStatus : List.of(1, 2, 3, 4, 5, 6)) {
            Long matchId = createMatch(admin, "M04状态" + matchStatus, "2099-06-05T20:00:00");
            jdbc.update("UPDATE team_match SET status = ? WHERE id = ?", matchStatus, matchId);
            signup(player, matchId, UUID.randomUUID().toString())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }

        Long deadlinePassed = createMatch(admin, "M04截止后", "2099-06-06T20:00:00");
        jdbc.update("UPDATE team_match SET signup_deadline = '2020-01-01 00:00:00' WHERE id = ?",
                deadlinePassed);
        signup(player, deadlinePassed, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long started = createMatch(admin, "M04已开赛", "2099-06-07T20:00:00");
        jdbc.update("UPDATE team_match SET start_at = '2020-01-01 12:00:00' WHERE id = ?", started);
        signup(player, started, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Cookie noProfile = insertAndLogin("13900000713", roleId("GUEST"), "NoProfile123!");
        Long open = createMatch(admin, "M04无档案", "2099-06-08T20:00:00");
        signup(noProfile, open, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        signup(player, 999999999L, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (2, 'M04跨队比赛', 1, '2099-06-01 10:00:00', '2099-06-01 12:00:00',
                    '2099-06-01 00:00:00', '2099-06-01 10:00:00', '测试场地', '2099', '橙队', '兰队',
                    30, 999998, 999998, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M04跨队比赛'", Long.class);
        createdMatchIds.add(crossTeamId);
        signup(player, crossTeamId, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void signupEnforcesPermissionIdempotencyAndPendingAccount() throws Exception {
        Cookie admin = bAdminLogin("13900000714");
        Long matchId = createMatch(admin, "M04权限幂等", "2099-06-09T20:00:00");
        setCapacities(matchId, 2, 1, 1);

        Long viewerRole = insertRole("M04_VIEWER", "match:view:any");
        Cookie viewer = insertAndLogin("13900000715", viewerRole, "Viewer123!");
        signup(viewer, matchId, UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Cookie player = playerLogin("13900000716", "幂等报名");
        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                        .cookie(player)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        String key = UUID.randomUUID().toString();
        MvcResult first = signup(player, matchId, key)
                .andExpect(status().isOk())
                .andReturn();
        MvcResult replay = signup(player, matchId, key)
                .andExpect(status().isOk())
                .andReturn();
        assertThat(JsonPath.<Number>read(replay.getResponse().getContentAsString(),
                "$.data.registration.id"))
                .isEqualTo(JsonPath.<Number>read(first.getResponse().getContentAsString(),
                        "$.data.registration.id"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(1);

        Cookie pending = insertUserAndLogin("13900000717", 0, roleId("GUEST"), "Pending123!");
        signup(pending, matchId, UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
    }

    @Test
    void concurrentSignupsNeverOversell() throws Exception {
        Cookie admin = bAdminLogin("13900000718");
        Long matchId = createMatch(admin, "M04并发抢名额", "2099-06-10T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        int contenders = 6;
        List<Cookie> cookies = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            cookies.add(playerLogin("1390000072" + i, "并发球员" + i));
        }

        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (Cookie cookie : cookies) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                                    .cookie(cookie)
                                    .with(csrf())
                                    .header("Idempotency-Key", UUID.randomUUID().toString()))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(3);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(contenders - 3);
        } finally {
            executor.shutdownNow();
        }

        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(3);
    }

    private ResultActions signup(Cookie cookie, Long matchId, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey));
    }

    private int countStatus(Long matchId, int status) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM registration
                WHERE match_id = ? AND status = ? AND is_deleted = 0
                """, Integer.class, matchId, status);
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
        insertPlayer(phone, name, 1, roleId("PLAYER"));
        return cLogin(phone, "Player123!");
    }

    private Cookie guestLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 0, roleId("GUEST"));
        return cLogin(phone, "Guest123!");
    }

    private void insertPlayer(String phone, String name, int type, Long roleId) {
        Long userId = insertUser(phone, 1, roleId);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 1, '[]', 3)
                """, userId, name, type);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
    }

    private Cookie insertAndLogin(String phone, Long roleId, String password) throws Exception {
        insertUser(phone, 1, roleId);
        return cLogin(phone, password);
    }

    private Cookie insertUserAndLogin(String phone, int status, Long roleId, String password)
            throws Exception {
        insertUser(phone, status, roleId);
        return cLogin(phone, password);
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
