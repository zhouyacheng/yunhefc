package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunhefc.grouping.domain.RosterFingerprint;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * M06 + M08 回填：事件选人按已保存分组校验（42203），报名前置优先（42201）。
 */
class EventGroupingIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void savedGroupingRejectsEachWrongTeamPlayer() throws Exception {
        Cookie admin = adminLogin("13900003101", "分组事件管理员");
        Long matchId = insertMatch("M06分组四字段", 3, 0);
        Long a1 = insertPlayer("13900003102", "分组A一", 1, 302, 2);
        Long a2 = insertPlayer("13900003103", "分组A二", 1, 303, 3);
        Long b1 = insertPlayer("13900003104", "分组B一", 1, 304, 2);
        Long b2 = insertPlayer("13900003105", "分组B二", 1, 305, 3);
        register(matchId, a1, 1, 0);
        register(matchId, a2, 1, 0);
        register(matchId, b1, 1, 0);
        register(matchId, b2, 1, 0);
        saveGrouping(matchId, List.of(a1, a2), List.of(b1, b2));

        adminCreate(admin, matchId, goalJson(0, a1, a2))
                .andExpect(status().isOk());
        adminCreate(admin, matchId, goalJson(1, b1, b2))
                .andExpect(status().isOk());

        adminCreate(admin, matchId, goalJson(0, b1, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203))
                .andExpect(jsonPath("$.message", containsString("playerId")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(b1))));
        adminCreate(admin, matchId, goalJson(0, a1, b1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203))
                .andExpect(jsonPath("$.message", containsString("assistPlayerId")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(b1))));
        adminCreate(admin, matchId, substitutionJson(0, b1, a1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203))
                .andExpect(jsonPath("$.message", containsString("inPlayerId")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(b1))));
        adminCreate(admin, matchId, substitutionJson(0, a2, b2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203))
                .andExpect(jsonPath("$.message", containsString("outPlayerId")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(b2))));
    }

    @Test
    void playerCreateAndPatchValidateSavedGrouping() throws Exception {
        Cookie player = playerLogin("13900003111", "分组本人");
        Long matchId = insertMatch("M06分组球员写入", 3, 0);
        Long self = playerId("13900003111");
        Long a2 = insertPlayer("13900003112", "分组队友", 1, 312, 3);
        Long b1 = insertPlayer("13900003113", "分组对手", 1, 313, 2);
        register(matchId, self, 1, 0);
        register(matchId, a2, 1, 0);
        register(matchId, b1, 1, 0);
        saveGrouping(matchId, List.of(self, a2), List.of(b1));

        createEvent(player, matchId, goalJson(0, self, null))
                .andExpect(status().isOk());
        createEvent(player, matchId, goalJson(0, b1, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));

        Long eventId = JsonPath.<Number>read(createEvent(player, matchId, goalJson(0, self, null))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();
        updateEvent(player, eventId, """
                {"team":1,"version":0}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203))
                .andExpect(jsonPath("$.message", containsString("playerId")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(self))));
    }

    @Test
    void adminPatchValidatesMergedTeamAndPlayer() throws Exception {
        Cookie admin = adminLogin("13900003121", "分组事件修改管理员");
        Long matchId = insertMatch("M06分组合并修改", 3, 0);
        Long a1 = insertPlayer("13900003122", "合并A一", 1, 322, 2);
        Long b1 = insertPlayer("13900003123", "合并B一", 1, 323, 3);
        register(matchId, a1, 1, 0);
        register(matchId, b1, 1, 0);
        saveGrouping(matchId, List.of(a1), List.of(b1));

        Long eventId = JsonPath.<Number>read(adminCreate(admin, matchId, goalJson(0, a1, null))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();
        adminUpdate(admin, eventId, """
                {"team":1,"version":0}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));
        adminUpdate(admin, eventId, """
                {"playerId":%d,"version":0}
                """.formatted(b1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));
        adminUpdate(admin, eventId, """
                {"assistPlayerId":%d,"version":0}
                """.formatted(b1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));
    }

    @Test
    void ownGoalChecksEventTeamAndStillScoresForOpponent() throws Exception {
        Cookie admin = adminLogin("13900003131", "分组乌龙管理员");
        Long matchId = insertMatch("M06分组乌龙", 3, 0);
        Long a1 = insertPlayer("13900003132", "乌龙A一", 1, 332, 2);
        Long b1 = insertPlayer("13900003133", "乌龙B一", 1, 333, 3);
        register(matchId, a1, 1, 0);
        register(matchId, b1, 1, 0);
        saveGrouping(matchId, List.of(a1), List.of(b1));

        adminCreate(admin, matchId, ownGoalJson(0, a1))
                .andExpect(status().isOk());
        adminCreate(admin, matchId, ownGoalJson(0, b1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.derivedScore.scoreHome").value(0))
                .andExpect(jsonPath("$.data.derivedScore.scoreAway").value(1));
    }

    @Test
    void draftAndMissingGroupingDoNotConstrain() throws Exception {
        Cookie admin = adminLogin("13900003141", "分组草稿管理员");
        Long draftMatch = insertMatch("M06分组草稿", 3, 0);
        Long a1 = insertPlayer("13900003142", "草稿A一", 1, 342, 2);
        Long b1 = insertPlayer("13900003143", "草稿B一", 1, 343, 3);
        register(draftMatch, a1, 1, 0);
        register(draftMatch, b1, 1, 0);
        insertGrouping(draftMatch, List.of(a1), List.of(b1), false);

        adminCreate(admin, draftMatch, goalJson(0, b1, null))
                .andExpect(status().isOk());

        Long plainMatch = insertMatch("M06无分组", 3, 0);
        Long unregistered = insertPlayer("13900003144", "无分组未报名", 1, 344, 2);
        register(plainMatch, a1, 1, 0);
        register(plainMatch, b1, 1, 0);
        adminCreate(admin, plainMatch, goalJson(0, b1, null))
                .andExpect(status().isOk());
        adminCreate(admin, plainMatch, goalJson(0, unregistered, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void registrationPreconditionWinsOverGroupingMismatch() throws Exception {
        Cookie admin = adminLogin("13900003151", "分组优先级管理员");
        Long matchId = insertMatch("M06分组优先级", 3, 0);
        Long a1 = insertPlayer("13900003152", "优先A一", 1, 352, 2);
        Long cancelled = insertPlayer("13900003153", "优先已取消", 1, 353, 3);
        Long disabled = insertPlayer("13900003154", "优先已注销", 1, 354, 2);
        register(matchId, a1, 1, 0);
        register(matchId, cancelled, 1, 2);
        register(matchId, disabled, 1, 0);
        jdbc.update("UPDATE app_user SET status = 2 WHERE phone = '13900003154'");
        saveGrouping(matchId, List.of(a1), List.of(cancelled, disabled));

        adminCreate(admin, matchId, goalJson(0, cancelled, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        adminCreate(admin, matchId, goalJson(0, disabled, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void guestOutsideSavedSnapshotCannotBeSelected() throws Exception {
        Cookie admin = adminLogin("13900003161", "分组外挂管理员");
        Long matchId = insertMatch("M06分组外挂", 3, 0);
        Long a1 = insertPlayer("13900003162", "外挂A一", 1, 362, 2);
        Long guest = insertPlayer("13900003163", "外挂球员", 0, 363, 3);
        register(matchId, a1, 1, 0);
        register(matchId, guest, 0, 0);
        saveGrouping(matchId, List.of(a1), List.of());

        adminCreate(admin, matchId, goalJson(0, guest, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42203))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(guest))));
    }

    @Test
    void groupingChangesDoNotRewriteExistingEvents() throws Exception {
        Cookie admin = adminLogin("13900003171", "分组不回溯管理员");
        Long matchId = insertMatch("M06分组不回溯", 3, 0);
        Long a1 = insertPlayer("13900003172", "回溯A一", 1, 372, 2);
        Long b1 = insertPlayer("13900003173", "回溯B一", 1, 373, 3);
        register(matchId, a1, 1, 0);
        register(matchId, b1, 1, 0);
        saveGrouping(matchId, List.of(a1), List.of(b1));

        Long eventId = JsonPath.<Number>read(adminCreate(admin, matchId, goalJson(0, a1, null))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();

        Long groupingId = jdbc.queryForObject(
                "SELECT id FROM match_grouping WHERE match_id = ?", Long.class, matchId);
        jdbc.update("UPDATE match_grouping_player SET team = 1 WHERE grouping_id = ? AND player_id = ?",
                groupingId, a1);
        jdbc.update("DELETE FROM match_grouping_player WHERE grouping_id = ?", groupingId);
        jdbc.update("DELETE FROM match_grouping WHERE id = ?", groupingId);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_event
                WHERE id = ? AND team = 0 AND player_id = ? AND is_deleted = 0
                """, Integer.class, eventId, a1)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(eventId))
                .andExpect(jsonPath("$.data.items[0].team").value(0))
                .andExpect(jsonPath("$.data.items[0].player.id").value(a1));
    }

    private ResultActions adminCreate(Cookie cookie, Long matchId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/events")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions adminUpdate(Cookie cookie, Long eventId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/b/events/" + eventId)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions createEvent(Cookie cookie, Long matchId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/events")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions updateEvent(Cookie cookie, Long eventId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/c/events/" + eventId)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String goalJson(int team, Long playerId, Long assistPlayerId) {
        return """
                {"type":0,"team":%d,"playerId":%d,"assistPlayerId":%s,
                 "inPlayerId":null,"outPlayerId":null,"minute":10,"half":0,"penalty":false}
                """.formatted(team, playerId, assistPlayerId);
    }

    private static String ownGoalJson(int team, Long playerId) {
        return """
                {"type":1,"team":%d,"playerId":%d,"assistPlayerId":null,
                 "inPlayerId":null,"outPlayerId":null,"minute":10,"half":0,"penalty":false}
                """.formatted(team, playerId);
    }

    private static String substitutionJson(int team, Long inPlayerId, Long outPlayerId) {
        return """
                {"type":2,"team":%d,"playerId":null,"assistPlayerId":null,
                 "inPlayerId":%d,"outPlayerId":%d,"minute":10,"half":0,"penalty":false}
                """.formatted(team, inPlayerId, outPlayerId);
    }

    private Long insertMatch(String title, int status, int includeGuests) {
        Instant startAt = Instant.now().minus(Duration.ofHours(1));
        Instant endAt = startAt.plus(Duration.ofHours(2));
        Instant signupDeadline = startAt.atZone(SHANGHAI).toLocalDate()
                .atStartOfDay(SHANGHAI).toInstant();
        Instant cancelLock = signupDeadline.plus(Duration.ofHours(10));
        long seasonNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) + 1
                FROM team_match WHERE team_id = 1 AND season = '2099'
                """, Long.class);
        long totalNo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(total_match_no), 0) + 1 FROM team_match WHERE team_id = 1",
                Long.class);
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, our_side, status,
                    manual_status, capacity, include_guests, season_match_no, total_match_no,
                    created_by)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', NULL, ?,
                        1, 30, ?, ?, ?, 1)
                """, title, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock), status,
                includeGuests, seasonNo, totalNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private void saveGrouping(Long matchId, List<Long> teamA, List<Long> teamB) {
        insertGrouping(matchId, teamA, teamB, true);
    }

    private void insertGrouping(Long matchId, List<Long> teamA, List<Long> teamB, boolean saved) {
        List<Long> all = Stream.concat(teamA.stream(), teamB.stream()).sorted().toList();
        jdbc.update("""
                INSERT INTO match_grouping (match_id, manual_weight, auto_weight,
                    roster_fingerprint, generated_at, created_by)
                VALUES (?, 70, 30, ?, NOW(3), 1)
                """, matchId, RosterFingerprint.of(all));
        Long groupingId = jdbc.queryForObject(
                "SELECT id FROM match_grouping WHERE match_id = ?", Long.class, matchId);
        teamA.forEach(playerId -> insertGroupingPlayer(groupingId, playerId, 0));
        teamB.forEach(playerId -> insertGroupingPlayer(groupingId, playerId, 1));
        if (saved) {
            jdbc.update("UPDATE match_grouping SET saved_at = NOW(3) WHERE id = ?", groupingId);
        }
    }

    private void insertGroupingPlayer(Long groupingId, Long playerId, int team) {
        jdbc.update("""
                INSERT INTO match_grouping_player (grouping_id, player_id, team)
                VALUES (?, ?, ?)
                """, groupingId, playerId, team);
    }

    private void register(Long matchId, Long playerId, int type, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, NOW(3))
                """, matchId, playerId, type, status);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 1, Integer.parseInt(phone.substring(phone.length() - 3)), 2);
        return cLogin(phone, "Player123!");
    }

    private Cookie adminLogin(String phone, String name) throws Exception {
        insertUser(phone, roleId("ADMIN"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, 1, 0, ?, '[]', 3)
                """, userId(phone), name, 100 + Integer.parseInt(phone.substring(phone.length() - 2)));
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Admin123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, "Admin123!")))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private Long insertPlayer(String phone, String name, int type, int jersey, int position) {
        Long userId = insertUser(phone, roleId(type == 1 ? "PLAYER" : "GUEST"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, ?, '[]', 3)
                """, userId, name, type, position, jersey);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private Long userId(String phone) {
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
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
