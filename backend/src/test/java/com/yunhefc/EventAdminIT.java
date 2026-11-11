package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * M06 管理员事件与比分（EVT-04/05/06/07）：任意事件、软删除、独立比分、版本与状态门。
 */
class EventAdminIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void adminCreatesAnyEventTypeAndSubstitutionWithoutOwnershipLimit() throws Exception {
        Cookie admin = adminLogin("13900002701", "管理员事件");
        Long matchId = insertMatch("M06管理员任意事件", 0, 0, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002701");
        Long teammateId = insertPlayer("13900002702", "管理员事件队友", 1, 702);
        register(matchId, playerId, 0);
        register(matchId, teammateId, 0);

        adminCreate(admin, matchId, """
                {"type":0,"team":1,"playerId":%d,"assistPlayerId":%d,
                 "inPlayerId":null,"outPlayerId":null,"minute":15,"half":0,"penalty":true}
                """.formatted(teammateId, playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.event.team").value(1))
                .andExpect(jsonPath("$.data.event.player.id").value(teammateId))
                .andExpect(jsonPath("$.data.event.assistPlayer.id").value(playerId))
                .andExpect(jsonPath("$.data.event.penalty").value(true));

        adminCreate(admin, matchId, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":30,"half":1,"penalty":false}
                """.formatted(playerId, teammateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.typeLabel").value("SUBSTITUTION"))
                .andExpect(jsonPath("$.data.event.inPlayer.id").value(playerId))
                .andExpect(jsonPath("$.data.event.outPlayer.id").value(teammateId));

        assertThat(auditCount("EVENT_CREATE")).isEqualTo(2);
    }

    @Test
    void adminStillMustPickConfirmedActiveRegistrations() throws Exception {
        Cookie admin = adminLogin("13900002703", "管理员选人");
        Long matchId = insertMatch("M06管理员选人", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long confirmed = insertPlayer("13900002704", "管理员确认", 1, 704);
        Long waitlisted = insertPlayer("13900002705", "管理员候补", 1, 705);
        Long unregistered = insertPlayer("13900002706", "管理员未报名", 1, 706);
        Long disabled = insertPlayer("13900002707", "管理员注销", 1, 707);
        register(matchId, confirmed, 0);
        register(matchId, waitlisted, 1);
        register(matchId, disabled, 0);
        jdbc.update("UPDATE app_user SET status = 2 WHERE phone = '13900002707'");

        adminCreate(admin, matchId, goalJson(waitlisted))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        adminCreate(admin, matchId, goalJson(unregistered))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        adminCreate(admin, matchId, goalJson(disabled))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        adminCreate(admin, matchId, goalJson(confirmed)).andExpect(status().isOk());
    }

    @Test
    void playerCannotUseAdminEventEndpoints() throws Exception {
        playerLogin("13900002708", "普通球员");
        Long matchId = insertMatch("M06后台权限", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002708");
        register(matchId, playerId, 0);
        // 正式球员有 attendance:view:any，可登录后台但不具备事件写权限。
        Cookie backendPlayer = backendLogin("13900002708");
        Long eventId = insertEvent(matchId, 0, 0, playerId, null, null, null, 10, 0, false);

        adminCreate(backendPlayer, matchId, goalJson(playerId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(patch("/api/v1/b/events/" + eventId)
                        .cookie(backendPlayer)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minute\":11,\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(delete("/api/v1/b/events/" + eventId)
                        .cookie(backendPlayer)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(patch("/api/v1/b/matches/" + matchId + "/score")
                        .cookie(backendPlayer)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scoreHome\":1,\"scoreAway\":0,\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void adminUpdatesAnyEventWithoutOwnershipOrTimeWindow() throws Exception {
        Cookie admin = adminLogin("13900002709", "管理员修改");
        Long playerId = playerId("13900002709");
        Long matchId = insertMatch("M06管理员修改", 1, null, 4,
                Instant.now().minus(Duration.ofHours(27)), Instant.now().minus(Duration.ofHours(25)));
        register(matchId, playerId, 0);
        Long eventId = insertEvent(matchId, 0, 0, playerId, null, null, null, 10, 0, false);

        adminUpdate(admin, eventId, """
                {"minute":21,"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.minute").value(21))
                .andExpect(jsonPath("$.data.event.version").value(1));

        adminUpdate(admin, eventId, """
                {"minute":22,"version":0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));
        assertThat(auditCount("EVENT_UPDATE")).isEqualTo(1);
    }

    @Test
    void deleteSoftDeletesRemovesDerivedScoreAndKeepsHistory() throws Exception {
        Cookie admin = adminLogin("13900002710", "管理员删除");
        Long matchId = insertMatch("M06删除事件", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002710");
        register(matchId, playerId, 0);
        Long eventId = insertEvent(matchId, 0, 0, playerId, null, null, null, 12, 0, false);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/events"))
                .andExpect(jsonPath("$.data.derivedScore.scoreHome").value(1))
                .andExpect(jsonPath("$.data.derivedScore.scoreAway").value(0));

        adminDelete(admin, eventId, """
                {"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventId").value(eventId))
                .andExpect(jsonPath("$.data.deletedAt").exists());

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.derivedScore").value(nullValue()));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.derivedScore").value(nullValue()));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_event
                WHERE id = ? AND is_deleted = 1 AND deleted_at IS NOT NULL
                  AND deleted_by = ?
                """, Integer.class, eventId, userId("13900002710"))).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_history
                WHERE event_id = ? AND action = 2 AND before_json IS NOT NULL
                  AND actor_user_id = ?
                """, Integer.class, eventId, userId("13900002710"))).isEqualTo(1);
        assertThat(auditCount("EVENT_DELETE")).isEqualTo(1);

        adminDelete(admin, eventId, """
                {"version":1}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void deleteRejectsVersionConflictCancelledAndPostponed() throws Exception {
        Cookie admin = adminLogin("13900002711", "管理员删除状态");
        Long playerId = playerId("13900002711");

        Long active = insertMatch("M06删除版本", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(active, playerId, 0);
        Long eventId = insertEvent(active, 0, 0, playerId, null, null, null, 10, 0, false);
        adminDelete(admin, eventId, """
                {"version":3}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM match_event WHERE id = ? AND is_deleted = 0
                """, Integer.class, eventId)).isEqualTo(1);

        for (int statusCode : new int[]{5, 6}) {
            String label = statusCode == 5 ? "延期" : "取消";
            Long matchId = insertMatch("M06删除" + label, 1, null, statusCode,
                    Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
            register(matchId, playerId, 0);
            Long id = insertEvent(matchId, 0, 0, playerId, null, null, null, 10, 0, false);
            adminDelete(admin, id, """
                    {"version":0}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }
    }

    @Test
    void scoreIsStoredIndependentlyAndReturnsDerivedScore() throws Exception {
        Cookie admin = adminLogin("13900002712", "管理员比分");
        Long matchId = insertMatch("M06存储比分", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002712");
        register(matchId, playerId, 0);
        insertEvent(matchId, 0, 0, playerId, null, null, null, 12, 0, false);

        adminScore(admin, matchId, """
                {"scoreHome":3,"scoreAway":2,"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score.scoreHome").value(3))
                .andExpect(jsonPath("$.data.score.scoreAway").value(2))
                .andExpect(jsonPath("$.data.score.derived.scoreHome").value(1))
                .andExpect(jsonPath("$.data.score.derived.scoreAway").value(0));

        assertThat(jdbc.queryForObject(
                "SELECT score_home FROM team_match WHERE id = ?", Integer.class, matchId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT score_away FROM team_match WHERE id = ?", Integer.class, matchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM team_match WHERE id = ?", Integer.class, matchId)).isEqualTo(1);
        assertThat(auditCount("SCORE_UPDATE")).isEqualTo(1);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scoreHome").value(3))
                .andExpect(jsonPath("$.data.scoreAway").value(2))
                .andExpect(jsonPath("$.data.derivedScore.scoreHome").value(1))
                .andExpect(jsonPath("$.data.derivedScore.scoreAway").value(0));

        adminScore(admin, matchId, """
                {"scoreHome":1,"scoreAway":1,"version":0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void scoreRejectsCancelledAndPostponed() throws Exception {
        Cookie admin = adminLogin("13900002713", "管理员比分状态");
        for (int statusCode : new int[]{5, 6}) {
            String label = statusCode == 5 ? "延期" : "取消";
            Long matchId = insertMatch("M06比分" + label, 1, null, statusCode,
                    Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
            adminScore(admin, matchId, """
                    {"scoreHome":1,"scoreAway":0,"version":0}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }
    }

    @Test
    void adminCreateAndUpdateRejectCancelledAndPostponed() throws Exception {
        Cookie admin = adminLogin("13900002715", "管理员事件状态");
        Long playerId = playerId("13900002715");
        for (int statusCode : new int[]{5, 6}) {
            String label = statusCode == 5 ? "延期" : "取消";
            Long matchId = insertMatch("M06事件" + label, 1, null, statusCode,
                    Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
            register(matchId, playerId, 0);
            adminCreate(admin, matchId, goalJson(playerId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));

            Long eventId = insertEvent(matchId, 0, 0, playerId, null, null, null, 10, 0, false);
            adminUpdate(admin, eventId, """
                    {"minute":11,"version":0}
                    """)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }
    }

    @Test
    void adminWritesRequireIdempotencyKey() throws Exception {
        Cookie admin = adminLogin("13900002714", "管理员幂等");
        Long matchId = insertMatch("M06管理员幂等", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002714");
        register(matchId, playerId, 0);
        Long eventId = insertEvent(matchId, 0, 0, playerId, null, null, null, 10, 0, false);

        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/events")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalJson(playerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(patch("/api/v1/b/events/" + eventId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minute\":11,\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(delete("/api/v1/b/events/" + eventId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(patch("/api/v1/b/matches/" + matchId + "/score")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scoreHome\":1,\"scoreAway\":0,\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
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

    private ResultActions adminDelete(Cookie cookie, Long eventId, String body) throws Exception {
        return mockMvc.perform(delete("/api/v1/b/events/" + eventId)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions adminScore(Cookie cookie, Long matchId, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/b/matches/" + matchId + "/score")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String goalJson(Long playerId) {
        return """
                {"type":0,"team":0,"playerId":%d,"assistPlayerId":null,
                 "inPlayerId":null,"outPlayerId":null,"minute":10,"half":0,"penalty":false}
                """.formatted(playerId);
    }

    private int auditCount(String action) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED'
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                """, Integer.class, action);
    }

    private Long insertMatch(String title, int type, Integer ourSide, int status,
                             Instant startAt, Instant endAt) {
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
                    cancel_lock, venue, season, home_team_name, away_team_name, our_side, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (1, ?, ?, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', ?, ?, 1, 30, ?, ?, 1)
                """, title, type, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock), ourSide, status,
                seasonNo, totalNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private Long insertEvent(Long matchId, int team, int type, Long playerId, Long assistPlayerId,
                             Long inPlayerId, Long outPlayerId, int minute, int half, boolean penalty) {
        jdbc.update("""
                INSERT INTO match_event (match_id, team, type, player_id, assist_player_id,
                    in_player_id, out_player_id, minute, half, penalty, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, matchId, team, type, playerId, assistPlayerId, inPlayerId, outPlayerId,
                minute, half, penalty);
        return jdbc.queryForObject("""
                SELECT MAX(id) FROM match_event WHERE match_id = ?
                """, Long.class, matchId);
    }

    private void register(Long matchId, Long playerId, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, 1, ?, NOW(3))
                """, matchId, playerId, status);
    }

    private Long playerId(String phone) {
        return jdbc.queryForObject("""
                SELECT p.id FROM player p JOIN app_user u ON u.id = p.user_id WHERE u.phone = ?
                """, Long.class, phone);
    }

    private Long userId(String phone) {
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 1, Integer.parseInt(phone.substring(phone.length() - 3)));
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

    private Long insertPlayer(String phone, String name, int type, int jersey) {
        Long userId = insertUser(phone, roleId(type == 1 ? "PLAYER" : "GUEST"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, ?, 2, ?, '[]', 3)
                """, userId, name, type, jersey);
        return jdbc.queryForObject("""
                SELECT id FROM player WHERE user_id = ?
                """, Long.class, userId);
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
