package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.grouping.domain.RosterFingerprint;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M08 分组生成（GRP-01/02/03）：权限、状态门、候选过滤、评分落库、覆盖确认与名单差异。
 */
class GroupingIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final String SEASON = "2026";

    @Test
    void permissionsAndVisibilityFollowThePermissionMatrix() throws Exception {
        Cookie admin = adminLogin("13900002801", "分组管理员");
        Long matchId = createMatch("M08权限赛", 1, 2, 1, SEASON);
        Long playerId = insertPlayer("13900002802", "正式球员", 1, 0, 4);
        Long teammateId = insertPlayer("13900002803", "正式队友", 1, 1, 3);
        register(matchId, playerId, 1, 0);
        register(matchId, teammateId, 1, 0);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/grouping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        Cookie guest = playerLogin("13900002804", "外挂访客", 0, 1, 3);
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/grouping").cookie(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Cookie player = cLogin("13900002802", "Player123!");
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/grouping").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teams.A.length()").value(0));

        Cookie backendPlayer = backendLogin("13900002802");
        getAdminGrouping(backendPlayer, matchId)
                .andExpect(status().isOk());
        generate(backendPlayer, matchId, "{}", key())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        generate(admin, matchId, "{}", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.grouping.teams.A.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    @Test
    void readAndGenerateApplyTypeStatusAndCandidateGates() throws Exception {
        Cookie admin = adminLogin("13900002811", "分组校验");
        Long emptyMatch = createMatch("M08无候选", 1, 2, 1, SEASON);
        getAdminGrouping(admin, emptyMatch)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teams.A.length()").value(0))
                .andExpect(jsonPath("$.data.teams.B.length()").value(0))
                .andExpect(jsonPath("$.data.diff").value(0))
                .andExpect(jsonPath("$.data.warnings.length()").value(0))
                .andExpect(jsonPath("$.data.savedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.scores.A").value(0))
                .andExpect(jsonPath("$.data.rosterFingerprint").value(nullValue()))
                .andExpect(jsonPath("$.data.generatedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.ungroupedPlayerIds.length()").value(0))
                .andExpect(jsonPath("$.data.rosterDiff.changed").value(false));
        generate(admin, emptyMatch, "{}", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        Long external = createMatch("M08对外赛", 0, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002812", "对外球员一", 1, 1, 3);
        Long p2 = insertPlayer("13900002813", "对外球员二", 1, 2, 3);
        register(external, p1, 1, 0);
        register(external, p2, 1, 0);
        getAdminGrouping(admin, external)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        generate(admin, external, "{}", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        Long notLocked = createMatch("M08未锁定", 1, 0, 1, SEASON);
        register(notLocked, p1, 1, 0);
        register(notLocked, p2, 1, 0);
        getAdminGrouping(admin, notLocked)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ungroupedPlayerIds.length()").value(2));
        generate(admin, notLocked, "{}", key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        getAdminGrouping(admin, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        Long oneCandidate = createMatch("M08一人候选", 1, 2, 1, SEASON);
        register(oneCandidate, p1, 1, 0);
        getAdminGrouping(admin, oneCandidate)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ungroupedPlayerIds.length()").value(1));
        generate(admin, oneCandidate, "{}", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void generatesDraftPersistsScoresAndRequiresConfirmationToOverwrite() throws Exception {
        Cookie admin = adminLogin("13900002821", "分组生成");
        updateSetting("grouping.minAppearances", "0");
        Long matchId = createMatch("M08生成赛", 1, 2, 1, SEASON);
        createMatch("M08已结束赛", 1, 4, 1, SEASON);
        Long goalkeeper = insertPlayer("13900002822", "门将", 1, 0, 5);
        Long defender = insertPlayer("13900002823", "后卫", 1, 1, 3);
        Long midfielder = insertPlayer("13900002824", "中场", 1, 2, 4);
        Long forward = insertPlayer("13900002825", "前锋", 1, 3, 2);
        register(matchId, goalkeeper, 1, 0);
        register(matchId, defender, 1, 0);
        register(matchId, midfielder, 1, 0);
        register(matchId, forward, 1, 0);
        insertStats(goalkeeper, 1, 0, 0);
        insertStats(defender, 0, 0, 0);
        insertStats(midfielder, 1, 2, 0);
        insertStats(forward, 1, 0, 1);

        String fingerprint = RosterFingerprint.of(List.of(goalkeeper, defender, midfielder, forward));
        generate(admin, matchId, "{}", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grouping.teams.A.length()")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.grouping.rosterFingerprint").value(fingerprint))
                .andExpect(jsonPath("$.data.grouping.generatedAt").exists())
                .andExpect(jsonPath("$.data.grouping.savedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.grouping.ungroupedPlayerIds.length()").value(0))
                .andExpect(jsonPath("$.data.grouping.rosterDiff.changed").value(false))
                .andExpect(jsonPath("$.data.grouping.weights.manualWeight").value(70))
                .andExpect(jsonPath("$.data.grouping.weights.autoWeight").value(30));

        Long groupingId = groupingId(matchId);
        Map<String, Object> main = jdbc.queryForMap("""
                SELECT manual_weight, auto_weight, roster_fingerprint, saved_at
                FROM match_grouping WHERE id = ?
                """, groupingId);
        assertThat(main.get("saved_at")).isNull();
        assertThat((BigDecimal) main.get("manual_weight")).isEqualByComparingTo("70");
        assertThat((BigDecimal) main.get("auto_weight")).isEqualByComparingTo("30");
        assertThat(main.get("roster_fingerprint")).isEqualTo(fingerprint);
        assertThat(groupingPlayerCount(groupingId)).isEqualTo(4);

        Map<String, Object> manualOnly = jdbc.queryForMap("""
                SELECT auto_score, final_score FROM match_grouping_player
                WHERE grouping_id = ? AND player_id = ?
                """, groupingId, defender);
        assertThat(manualOnly.get("auto_score")).isNull();
        assertThat((BigDecimal) manualOnly.get("final_score")).isEqualByComparingTo("3.00");

        Map<String, Object> blended = jdbc.queryForMap("""
                SELECT auto_score, final_score FROM match_grouping_player
                WHERE grouping_id = ? AND player_id = ?
                """, groupingId, goalkeeper);
        assertThat((BigDecimal) blended.get("auto_score")).isEqualByComparingTo("3.00");
        assertThat((BigDecimal) blended.get("final_score")).isEqualByComparingTo("4.40");

        assertThat(auditCount("grouping.generate")).isEqualTo(1);

        jdbc.update("UPDATE match_grouping SET saved_at = NOW(3) WHERE id = ?", groupingId);
        generate(admin, matchId, "{}", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42202));

        generate(admin, matchId, "{\"confirm\":true}", key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grouping.savedAt").value(nullValue()));
        assertThat(groupingId(matchId)).isEqualTo(groupingId);
        assertThat(groupingPlayerCount(groupingId)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT saved_at FROM match_grouping WHERE id = ?", Timestamp.class, groupingId))
                .isNull();
        assertThat(auditCount("grouping.generate")).isEqualTo(2);
    }

    @Test
    void filtersGuestsAndInactiveAccountsAccordingToTheMatchSwitch() throws Exception {
        Cookie admin = adminLogin("13900002831", "分组过滤");
        Long withoutGuests = createMatch("M08不含外挂", 1, 2, 0, SEASON);
        Long p1 = insertPlayer("13900002832", "正式一", 1, 0, 3);
        Long p2 = insertPlayer("13900002833", "外挂二", 0, 1, 3);
        Long p3 = insertPlayer("13900002834", "正式三", 1, 1, 3);
        Long p4 = insertPlayer("13900002835", "外挂四", 0, 2, 3);
        Long p5 = insertPlayer("13900002836", "注销五", 1, 2, 3);
        Long p6 = insertPlayer("13900002837", "正式六", 1, 3, 3);
        jdbc.update("UPDATE app_user SET status = 2 WHERE id = (SELECT user_id FROM player WHERE id = ?)",
                p5);
        register(withoutGuests, p1, 1, 0);
        register(withoutGuests, p2, 0, 0);
        register(withoutGuests, p3, 0, 0);
        register(withoutGuests, p4, 1, 0);
        register(withoutGuests, p5, 1, 0);
        register(withoutGuests, p6, 1, 0);

        generate(admin, withoutGuests, "{}", key()).andExpect(status().isOk());
        Long groupingId = groupingId(withoutGuests);
        assertThat(groupingPlayerCount(groupingId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT roster_fingerprint FROM match_grouping WHERE id = ?", String.class, groupingId))
                .isEqualTo(RosterFingerprint.of(List.of(p1, p6)));

        Long withGuests = createMatch("M08含外挂", 1, 2, 1, SEASON);
        register(withGuests, p1, 1, 0);
        register(withGuests, p2, 0, 0);
        register(withGuests, p3, 0, 0);
        register(withGuests, p4, 1, 0);
        register(withGuests, p5, 1, 0);
        register(withGuests, p6, 1, 0);
        generate(admin, withGuests, "{}", key()).andExpect(status().isOk());
        assertThat(groupingPlayerCount(groupingId(withGuests))).isEqualTo(5);
    }

    @Test
    void readReportsUngroupedAndRosterDiffWithoutChangingTheSavedSnapshot() throws Exception {
        Cookie admin = adminLogin("13900002841", "分组差异");
        Long matchId = createMatch("M08名单差异", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002842", "球员一", 1, 0, 3);
        Long p2 = insertPlayer("13900002843", "球员二", 1, 1, 3);
        Long p3 = insertPlayer("13900002844", "球员三", 1, 2, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);
        register(matchId, p3, 1, 0);
        generate(admin, matchId, "{}", key()).andExpect(status().isOk());

        getAdminGrouping(admin, matchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ungroupedPlayerIds.length()").value(0))
                .andExpect(jsonPath("$.data.rosterDiff.changed").value(false));

        Long p4 = insertPlayer("13900002845", "球员四", 1, 3, 3);
        register(matchId, p4, 1, 0);
        getAdminGrouping(admin, matchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rosterDiff.changed").value(true))
                .andExpect(jsonPath("$.data.rosterDiff.addedPlayerIds", hasItem(p4.intValue())))
                .andExpect(jsonPath("$.data.ungroupedPlayerIds", hasItem(p4.intValue())));

        jdbc.update("UPDATE registration SET status = 2, cancelled_at = NOW(3) "
                + "WHERE match_id = ? AND player_id = ?", matchId, p1);
        getAdminGrouping(admin, matchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rosterDiff.removedPlayerIds", hasItem(p1.intValue())))
                .andExpect(jsonPath("$.data.teams.A.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(0)));
    }

    @Test
    void idempotentReplayAndFailedAttemptsDoNotBlockRetry() throws Exception {
        Cookie admin = adminLogin("13900002851", "分组幂等");
        Long matchId = createMatch("M08幂等赛", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002852", "幂等一", 1, 0, 3);
        Long p2 = insertPlayer("13900002853", "幂等二", 1, 1, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);

        String firstKey = key();
        generate(admin, matchId, "{}", firstKey).andExpect(status().isOk());
        generate(admin, matchId, "{}", firstKey).andExpect(status().isOk());
        assertThat(groupingCount(matchId)).isEqualTo(1);
        assertThat(groupingPlayerCount(groupingId(matchId))).isEqualTo(2);

        generate(admin, matchId, "{\"confirm\":true}", firstKey)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        String retryKey = key();
        generate(admin, matchId, "{}", retryKey)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42202));
        generate(admin, matchId, "{\"confirm\":true}", retryKey)
                .andExpect(status().isOk());
        assertThat(groupingPlayerCount(groupingId(matchId))).isEqualTo(2);
    }

    private ResultActions getAdminGrouping(Cookie cookie, Long matchId) throws Exception {
        return mockMvc.perform(get("/api/v1/b/matches/" + matchId + "/grouping").cookie(cookie));
    }

    private ResultActions generate(Cookie cookie, Long matchId, String body, String key)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/grouping/generate")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private Long groupingId(Long matchId) {
        return jdbc.queryForObject(
                "SELECT id FROM match_grouping WHERE match_id = ?", Long.class, matchId);
    }

    private int groupingCount(Long matchId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM match_grouping WHERE match_id = ?", Integer.class, matchId);
    }

    private int groupingPlayerCount(Long groupingId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM match_grouping_player WHERE grouping_id = ?",
                Integer.class, groupingId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                """, Integer.class, action);
    }

    private void updateSetting(String settingKey, String value) {
        jdbc.update("""
                UPDATE system_setting SET setting_value = ?
                WHERE team_id = 1 AND setting_key = ?
                """, value, settingKey);
    }

    private void insertStats(Long playerId, int appearances, int goals, int assists) {
        jdbc.update("""
                INSERT INTO player_stats_summary (team_id, player_id, season, appearances, goals, assists)
                VALUES (1, ?, ?, ?, ?, ?)
                """, playerId, SEASON, appearances, goals, assists);
    }

    private Long createMatch(String title, int type, int status, int includeGuests, String season) {
        Instant start = status == 4
                ? Instant.now().minus(Duration.ofHours(3))
                : Instant.now().plus(Duration.ofDays(1));
        Instant end = start.plus(Duration.ofHours(2));
        Instant signupDeadline = start.atZone(SHANGHAI).toLocalDate()
                .atStartOfDay(SHANGHAI).toInstant();
        Instant cancelLock = signupDeadline.plus(Duration.ofHours(10));
        long seasonNo = jdbc.queryForObject("""
                SELECT COALESCE(MAX(season_match_no), 0) + 1
                FROM team_match WHERE team_id = 1 AND season = ?
                """, Long.class, season);
        long totalNo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(total_match_no), 0) + 1 FROM team_match WHERE team_id = 1",
                Long.class);
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, our_side, status,
                    manual_status, capacity, guest_capacity, waitlist_capacity, include_guests,
                    season_match_no, total_match_no, created_by)
                VALUES (1, ?, ?, ?, ?, ?, ?, '测试场地', ?, '橙队', '兰队', ?, ?, 1, 30, 4, 2, ?,
                        ?, ?, 1)
                """, title, type, Timestamp.from(start), Timestamp.from(end),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock), season,
                type == 1 || type == 2 ? null : 0, status, includeGuests, seasonNo, totalNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private void register(Long matchId, Long playerId, int type, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, NOW(3))
                """, matchId, playerId, type, status);
    }

    private Cookie playerLogin(String phone, String name, int type, int position, int skill)
            throws Exception {
        insertPlayer(phone, name, type, position, skill);
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

    private Long insertPlayer(String phone, String name, int type, int position, int skill) {
        Long userId = insertUser(phone, roleId(type == 1 ? "PLAYER" : "GUEST"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, ?, '[]', ?)
                """, userId, name, type, position,
                200 + Integer.parseInt(phone.substring(phone.length() - 2)), skill);
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
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Cookie backendLogin(String phone) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Player123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, "Player123!")))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
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
