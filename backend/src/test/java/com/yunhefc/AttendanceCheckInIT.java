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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

class AttendanceCheckInIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void checkInWithinWindowCreatesPresentSelfRecordAndFillsReg04() throws Exception {
        Cookie player = playerLogin("13900002501", "签到本人");
        Long matchId = insertMatch("M05签到窗口内", Instant.now().plus(Duration.ofHours(2)), 2);
        register(matchId, playerId("13900002501"), 1, 0);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attendance").value(nullValue()));

        MvcResult first = mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attendance.id").isNumber())
                .andExpect(jsonPath("$.data.attendance.playerId").value(playerId("13900002501")))
                .andExpect(jsonPath("$.data.attendance.status").value(0))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"))
                .andExpect(jsonPath("$.data.attendance.source").value(0))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("SELF"))
                .andExpect(jsonPath("$.data.attendance.recordedAt").exists())
                .andReturn();
        Long attendanceId = JsonPath.<Number>read(
                first.getResponse().getContentAsString(), "$.data.attendance.id").longValue();

        // 新幂等键重试仍返回同一条记录，数据库只有一条。
        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(attendanceId));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM attendance WHERE match_id = ? AND player_id = ?
                """, Integer.class, matchId, playerId("13900002501"))).isEqualTo(1);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(attendanceId))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/registrations/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(0))
                .andExpect(jsonPath("$.data.registration.statusLabel").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.attendance.id").value(attendanceId))
                .andExpect(jsonPath("$.data.attendance.status").value(0))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"))
                .andExpect(jsonPath("$.data.attendance.source").value(0))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("SELF"));

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(player)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rejectsCheckInOutsideWindowAndWithoutConfirmedRegistration() throws Exception {
        Cookie confirmed = playerLogin("13900002502", "签到窗口外");
        Long tooEarly = insertMatch("M05签到未开始", Instant.now().plus(Duration.ofHours(10)), 2);
        register(tooEarly, playerId("13900002502"), 1, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + tooEarly + "/check-in")
                        .cookie(confirmed)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        Long tooLate = insertMatch("M05签到已结束", Instant.now().minus(Duration.ofHours(25)), 2);
        register(tooLate, playerId("13900002502"), 1, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + tooLate + "/check-in")
                        .cookie(confirmed)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        Long open = insertMatch("M05仅确认可签", Instant.now().plus(Duration.ofHours(2)), 2);
        Cookie waitlisted = playerLogin("13900002503", "签到候补");
        register(open, playerId("13900002503"), 1, 1);

        mockMvc.perform(post("/api/v1/c/matches/" + open + "/check-in")
                        .cookie(waitlisted)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        Cookie unregistered = playerLogin("13900002504", "签到未报名");
        mockMvc.perform(post("/api/v1/c/matches/" + open + "/check-in")
                        .cookie(unregistered)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance", Integer.class)).isZero();
    }

    @Test
    void cancelledAndPostponedMatchesReturnStateConflict() throws Exception {
        Cookie player = playerLogin("13900002505", "签到作废场次");
        Long cancelled = insertMatch("M05签到已取消", Instant.now().plus(Duration.ofHours(2)), 6);
        register(cancelled, playerId("13900002505"), 1, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + cancelled + "/check-in")
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long postponed = insertMatch("M05签到已延期", Instant.now().plus(Duration.ofHours(2)), 5);
        register(postponed, playerId("13900002505"), 1, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + postponed + "/check-in")
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
    }

    @Test
    void existingAdminSystemOrSelfAttendanceIsReturnedUnchanged() throws Exception {
        Cookie adminMarked = playerLogin("13900002506", "签到管理员优先");
        Long matchId = insertMatch("M05签到已有记录", Instant.now().plus(Duration.ofHours(2)), 2);
        Long adminMarkedPlayer = playerId("13900002506");
        register(matchId, adminMarkedPlayer, 1, 0);
        Long adminRecord = insertAttendance(matchId, adminMarkedPlayer, 0, 1);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(adminMarked)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(adminRecord))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"))
                .andExpect(jsonPath("$.data.attendance.source").value(1))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("ADMIN"));

        Cookie systemMarked = playerLogin("13900002507", "签到系统缺勤");
        Long systemMarkedPlayer = playerId("13900002507");
        register(matchId, systemMarkedPlayer, 1, 0);
        Long systemRecord = insertAttendance(matchId, systemMarkedPlayer, 1, 2);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(systemMarked)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(systemRecord))
                .andExpect(jsonPath("$.data.attendance.status").value(1))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("ABSENT"))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("SYSTEM"));

        Cookie selfMarked = playerLogin("13900002508", "签到自助重试");
        Long selfMarkedPlayer = playerId("13900002508");
        register(matchId, selfMarkedPlayer, 1, 0);
        Long selfRecord = insertAttendance(matchId, selfMarkedPlayer, 0, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(selfMarked)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(selfRecord))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("SELF"));
    }

    @Test
    void guestSeesOnlyOwnAttendanceAndCrossTeamMatchIsNotFound() throws Exception {
        Cookie guest = guestLogin("13900002509", "签到外挂");
        Long matchId = insertMatch("M05外挂签到", Instant.now().plus(Duration.ofHours(2)), 2);
        Long guestPlayer = playerId("13900002509");
        register(matchId, guestPlayer, 0, 0);

        MvcResult result = mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(guest)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.playerId").value(guestPlayer))
                .andReturn();
        Long attendanceId = JsonPath.<Number>read(
                result.getResponse().getContentAsString(), "$.data.attendance.id").longValue();

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance/me").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(attendanceId));

        Cookie otherGuest = guestLogin("13900002510", "签到外挂他人");
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance/me").cookie(otherGuest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance").value(nullValue()));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (2, 'M05签到跨队', 1, NOW(3) + INTERVAL 2 HOUR, NOW(3) + INTERVAL 4 HOUR,
                    NOW(3), NOW(3) + INTERVAL 10 HOUR, '测试场地', '2099', '橙队', '兰队',
                    2, 1, 30, 999991, 999991, 1)
                """);
        Long crossTeamId = jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M05签到跨队'", Long.class);

        mockMvc.perform(post("/api/v1/c/matches/" + crossTeamId + "/check-in")
                        .cookie(guest)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/v1/c/matches/" + crossTeamId + "/attendance/me").cookie(guest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    /**
     * 回归：登录响应返回的 CSRF token 必须能跨过一个已登录 GET 继续使用；
     * 否则前端首次写请求会先吃 403，再重取 token 重试。
     */
    @Test
    void loginIssuedCsrfTokenSurvivesFirstAuthenticatedRead() throws Exception {
        insertPlayer("13900002512", "签到令牌", 1, 512);
        Long matchId = insertMatch("M05签到令牌", Instant.now().plus(Duration.ofHours(2)), 2);
        register(matchId, playerId("13900002512"), 1, 0);

        CLogin login = loginWithRealCsrf("13900002512", "Player123!");

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance/me")
                        .cookie(login.cookie()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(login.cookie())
                        .header("X-XSRF-TOKEN", login.csrfToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"));
    }

    @Test
    void checkInIsDrivenByWindowNotMatchStatus() throws Exception {
        Cookie player = playerLogin("13900002511", "签到状态无关");
        Long matchId = insertMatch("M05报名中也可签", Instant.now().plus(Duration.ofHours(2)), 0);
        register(matchId, playerId("13900002511"), 1, 0);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/check-in")
                        .cookie(player)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"));
    }

    private Long insertMatch(String title, Instant startAt, int status) {
        Instant endAt = startAt.plus(Duration.ofHours(2));
        Instant signupDeadline = startAt.atZone(SHANGHAI).toLocalDate()
                .atStartOfDay(SHANGHAI).toInstant();
        Instant cancelLock = signupDeadline.plus(Duration.ofHours(10));
        long seasonNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) + 1
                FROM team_match WHERE team_id = 1 AND season = '2099'
                """, Long.class);
        long totalNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(total_match_no), 0) + 1 FROM team_match WHERE team_id = 1
                """, Long.class);
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', ?, 1, 30, ?, ?, 1)
                """, title, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock), status, seasonNo, totalNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private void register(Long matchId, Long playerId, int type, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, NOW(3))
                """, matchId, playerId, type, status);
    }

    private Long insertAttendance(Long matchId, Long playerId, int status, int source) {
        jdbc.update("""
                INSERT INTO attendance (match_id, player_id, status, source, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, ?, NOW(3))
                """, matchId, playerId, status, source, source == 1 ? 1 : null);
        return jdbc.queryForObject(
                "SELECT id FROM attendance WHERE match_id = ? AND player_id = ?",
                Long.class, matchId, playerId);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
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
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
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

    private record CLogin(Cookie cookie, String csrfToken) {
    }

    /**
     * 使用真实 CsrfTokenRepository（不走 MockMvc 的 csrf() 测试仓库），验证登录轮换后的 token。
     */
    private CLogin loginWithRealCsrf(String phone, String password) throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/c/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie preLoginCookie = cookie(csrf, "YHFC_C");

        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult login = mockMvc.perform(post("/api/v1/c/auth/login")
                        .cookie(preLoginCookie)
                        .header("X-XSRF-TOKEN", csrfToken(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return new CLogin(cookie(login, "YHFC_C"), JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.csrfToken"));
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
}
