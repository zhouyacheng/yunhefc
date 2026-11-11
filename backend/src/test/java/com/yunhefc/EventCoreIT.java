package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * M06 事件核心（EVT-01/02/03）：推导比分、时间线、权限窗口、字段矩阵、历史与审计。
 */
class EventCoreIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void playerRecordsGoalAndOwnGoalAndDerivedScoreIsVisibleEverywhere() throws Exception {
        Cookie player = playerLogin("13900002601", "事件本人");
        Cookie teammate = playerLogin("13900002602", "事件队友");
        Long matchId = insertMatch("M06推导比分", 0, 0, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002601");
        Long teammateId = playerId("13900002602");
        register(matchId, playerId, 0);
        register(matchId, teammateId, 0);

        MvcResult goal = createEvent(player, matchId, """
                {"type":0,"team":0,"playerId":%d,"assistPlayerId":%d,
                 "inPlayerId":null,"outPlayerId":null,"minute":12,"half":0,"penalty":true}
                """.formatted(playerId, teammateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.event.type").value(0))
                .andExpect(jsonPath("$.data.event.typeLabel").value("GOAL"))
                .andExpect(jsonPath("$.data.event.team").value(0))
                .andExpect(jsonPath("$.data.event.teamLabel").value("HOME"))
                .andExpect(jsonPath("$.data.event.half").value(0))
                .andExpect(jsonPath("$.data.event.halfLabel").value("FIRST"))
                .andExpect(jsonPath("$.data.event.player.id").value(playerId))
                .andExpect(jsonPath("$.data.event.assistPlayer.id").value(teammateId))
                .andExpect(jsonPath("$.data.event.minute").value(12))
                .andExpect(jsonPath("$.data.event.penalty").value(true))
                .andExpect(jsonPath("$.data.event.version").value(0))
                .andExpect(jsonPath("$.data.event.updatedAt").exists())
                .andReturn();
        assertThat(JsonPath.<Number>read(goal.getResponse().getContentAsString(),
                "$.data.event.id")).isNotNull();

        createEvent(player, matchId, """
                {"type":1,"team":0,"playerId":%d,"assistPlayerId":null,
                 "inPlayerId":null,"outPlayerId":null,"minute":20,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.typeLabel").value("OWN_GOAL"));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value(0))
                .andExpect(jsonPath("$.data.items[1].type").value(1))
                .andExpect(jsonPath("$.data.derivedScore.scoreHome").value(1))
                .andExpect(jsonPath("$.data.derivedScore.scoreAway").value(1));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.derivedScore.scoreHome").value(1))
                .andExpect(jsonPath("$.data.derivedScore.scoreAway").value(1));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_history WHERE action = 0 AND actor_user_id = ?
                  AND before_json IS NULL AND after_json IS NOT NULL
                  AND request_id IS NOT NULL AND request_id <> ''
                """, Integer.class, userId("13900002601"))).isEqualTo(2);
        assertThat(auditCount("EVENT_CREATE")).isEqualTo(2);
    }

    @Test
    void timelineOrdersByHalfMinuteGoalPriorityThenIdAndHidesDeleted() throws Exception {
        Cookie admin = adminLogin("13900002603", "事件排序");
        Long matchId = insertMatch("M06时间线排序", 0, 0, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002603");
        register(matchId, playerId, 0);

        Long secondHalf = insertEvent(matchId, 1, 0, playerId, null, null, null, 5, 1, false);
        Long yellow = insertEvent(matchId, 0, 3, playerId, null, null, null, 10, 0, false);
        Long ownGoal = insertEvent(matchId, 0, 1, playerId, null, null, null, 10, 0, false);
        Long goal = insertEvent(matchId, 0, 0, playerId, null, null, null, 10, 0, false);
        Long red = insertEvent(matchId, 0, 4, playerId, null, null, null, 8, 0, false);
        insertEvent(matchId, 0, 0, playerId, null, null, null, 1, 0, false);
        jdbc.update("UPDATE match_event SET is_deleted = 1, deleted_at = NOW(3) "
                + "WHERE match_id = ? AND minute = 1", matchId);
        jdbc.update("UPDATE match_event SET deleted_at = NOW(3) WHERE id = ?", red);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andExpect(jsonPath("$.data.items[0].id").value(ownGoal))
                .andExpect(jsonPath("$.data.items[1].id").value(goal))
                .andExpect(jsonPath("$.data.items[2].id").value(yellow))
                .andExpect(jsonPath("$.data.items[3].id").value(secondHalf));
    }

    @Test
    void fieldMatrixRejectsStructuralAndBusinessViolations() throws Exception {
        Cookie admin = adminLogin("13900002604", "事件字段矩阵");
        Long matchId = insertMatch("M06字段矩阵", 0, 0, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long playerId = playerId("13900002604");
        Long teammateId = insertPlayer("13900002605", "事件字段队友", 1, 605);
        register(matchId, playerId, 0);
        register(matchId, teammateId, 0);

        createEvent(admin, matchId, """
                {"type":9,"team":0,"playerId":%d,"minute":10,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEvent(admin, matchId, """
                {"type":0,"team":2,"playerId":%d,"minute":10,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEvent(admin, matchId, """
                {"type":0,"team":0,"playerId":%d,"minute":0,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEvent(admin, matchId, """
                {"type":0,"team":0,"playerId":%d,"minute":121,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEvent(admin, matchId, """
                {"type":0,"team":0,"playerId":%d,"minute":10,"half":2,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        createEvent(admin, matchId, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":null,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId, playerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, """
                {"type":2,"team":0,"playerId":%d,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId, playerId, teammateId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, """
                {"type":0,"team":0,"playerId":%d,"assistPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId, playerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, """
                {"type":1,"team":0,"playerId":%d,"assistPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId, teammateId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, """
                {"type":3,"team":0,"playerId":%d,"inPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId, teammateId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM match_event", Integer.class)).isZero();
    }

    @Test
    void createRespectsWindowTeamLockAndSubstitutionPermission() throws Exception {
        Cookie player = playerLogin("13900002606", "事件窗口");
        Cookie teammate = playerLogin("13900002607", "事件窗口队友");
        Long playerId = playerId("13900002606");
        Long teammateId = playerId("13900002607");

        Long overdue = insertMatch("M06窗口外", 0, 0, 4,
                Instant.now().minus(Duration.ofHours(27)), Instant.now().minus(Duration.ofHours(25)));
        register(overdue, playerId, 0);
        register(overdue, teammateId, 0);
        createEvent(player, overdue, goalJson(playerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        Cookie admin = adminLogin("13900002608", "事件窗口管理员");
        createEvent(admin, overdue, goalJson(playerId)).andExpect(status().isOk());

        Long withinWindow = insertMatch("M06窗口内", 0, 0, 4,
                Instant.now().minus(Duration.ofHours(25)), Instant.now().minus(Duration.ofHours(23)));
        register(withinWindow, playerId, 0);
        register(withinWindow, teammateId, 0);
        createEvent(player, withinWindow, goalJson(playerId)).andExpect(status().isOk());

        Long official = insertMatch("M06对外锁定球队", 0, 0, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(official, playerId, 0);
        register(official, teammateId, 0);
        createEvent(player, official, goalJson(teammateId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(player, official, """
                {"type":0,"team":1,"playerId":%d,"minute":10,"half":0,"penalty":false}
                """.formatted(playerId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(player, official, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(playerId, teammateId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        Long officialGoal = JsonPath.<Number>read(createEvent(player, official, goalJson(playerId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();
        updateEvent(player, officialGoal, """
                {"team":1,"version":0}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        mockMvc.perform(post("/api/v1/c/matches/" + official + "/events")
                        .cookie(player)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalJson(playerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        createEvent(admin, official, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":30,"half":0,"penalty":false}
                """.formatted(playerId, teammateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.typeLabel").value("SUBSTITUTION"))
                .andExpect(jsonPath("$.data.event.inPlayer.id").value(playerId))
                .andExpect(jsonPath("$.data.event.outPlayer.id").value(teammateId));

        Long internal = insertMatch("M06队内任意球员", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(internal, playerId, 0);
        register(internal, teammateId, 0);
        createEvent(player, internal, """
                {"type":0,"team":1,"playerId":%d,"assistPlayerId":%d,
                 "minute":15,"half":0,"penalty":false}
                """.formatted(teammateId, playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.team").value(1))
                .andExpect(jsonPath("$.data.event.player.id").value(teammateId));
        createEvent(player, internal, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":30,"half":0,"penalty":false}
                """.formatted(playerId, teammateId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void selectedPlayersMustBeConfirmedActiveRegistrations() throws Exception {
        Cookie admin = adminLogin("13900002609", "事件选人校验");
        Long matchId = insertMatch("M06选人报名", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        Long confirmed = insertPlayer("13900002610", "事件确认", 1, 610);
        Long waitlisted = insertPlayer("13900002611", "事件候补", 1, 611);
        Long unregistered = insertPlayer("13900002612", "事件未报名", 1, 612);
        Long disabled = insertPlayer("13900002613", "事件已注销", 1, 613);
        Long assistUnconfirmed = insertPlayer("13900002614", "事件助攻未报名", 1, 614);
        register(matchId, confirmed, 0);
        register(matchId, waitlisted, 1);
        register(matchId, disabled, 0);
        jdbc.update("UPDATE app_user SET status = 2 WHERE phone = '13900002613'");

        createEvent(admin, matchId, goalJson(waitlisted))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, goalJson(unregistered))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, goalJson(disabled))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, """
                {"type":0,"team":0,"playerId":%d,"assistPlayerId":%d,
                 "minute":10,"half":0,"penalty":false}
                """.formatted(confirmed, assistUnconfirmed))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        createEvent(admin, matchId, goalJson(confirmed)).andExpect(status().isOk());
    }

    @Test
    void updateChecksOwnershipWindowVersionAndMatchState() throws Exception {
        Cookie player = playerLogin("13900002615", "事件修改本人");
        Cookie other = playerLogin("13900002616", "事件修改他人");
        Cookie admin = adminLogin("13900002617", "事件修改管理员");
        Long playerId = playerId("13900002615");
        Long otherId = playerId("13900002616");
        Long matchId = insertMatch("M06修改校验", 1, null, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(matchId, playerId, 0);
        register(matchId, otherId, 0);

        Long eventId = JsonPath.<Number>read(createEvent(player, matchId, goalJson(playerId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();

        updateEvent(player, eventId, """
                {"minute":20,"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.minute").value(20))
                .andExpect(jsonPath("$.data.event.version").value(1));
        updateEvent(player, eventId, """
                {"minute":21,"version":0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
        updateEvent(other, eventId, """
                {"minute":22,"version":1}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        updateEvent(player, eventId, """
                {"playerId":%d,"version":1}
                """.formatted(otherId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_history
                WHERE event_id = ? AND action = 1 AND before_json IS NOT NULL
                  AND after_json IS NOT NULL AND actor_user_id = ?
                """, Integer.class, eventId, userId("13900002615"))).isEqualTo(1);
        assertThat(auditCount("EVENT_UPDATE")).isEqualTo(1);

        Long substitution = JsonPath.<Number>read(createEvent(admin, matchId, """
                {"type":2,"team":0,"playerId":null,"inPlayerId":%d,"outPlayerId":%d,
                 "minute":30,"half":0,"penalty":false}
                """.formatted(playerId, otherId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();
        updateEvent(player, substitution, """
                {"minute":31,"version":0}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        updateEvent(admin, substitution, """
                {"minute":31,"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.minute").value(31));

        Long overdue = insertMatch("M06修改超时", 1, null, 4,
                Instant.now().minus(Duration.ofHours(27)), Instant.now().minus(Duration.ofHours(25)));
        register(overdue, playerId, 0);
        Long overdueEvent = insertEvent(overdue, 0, 0, playerId, null, null, null, 10, 0, false);
        updateEvent(player, overdueEvent, """
                {"minute":11,"version":0}
                """)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        updateEvent(admin, overdueEvent, """
                {"minute":11,"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.minute").value(11));

        Long cancelled = insertMatch("M06修改已取消", 1, null, 6,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(cancelled, playerId, 0);
        Long cancelledEvent = insertEvent(cancelled, 0, 0, playerId, null, null, null, 10, 0, false);
        updateEvent(admin, cancelledEvent, """
                {"minute":11,"version":0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long postponed = insertMatch("M06修改已延期", 1, null, 5,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(postponed, playerId, 0);
        Long postponedEvent = insertEvent(postponed, 0, 0, playerId, null, null, null, 10, 0, false);
        updateEvent(admin, postponedEvent, """
                {"minute":11,"version":0}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
    }

    @Test
    void createRequiresInProgressOrFinishedAndRejectsCancelPostpone() throws Exception {
        Cookie admin = adminLogin("13900002618", "事件状态校验");
        Long playerId = playerId("13900002618");
        Long locked = insertMatch("M06锁定不可录", 1, null, 2,
                Instant.now().plus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(3)));
        register(locked, playerId, 0);
        createEvent(admin, locked, goalJson(playerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long cancelled = insertMatch("M06取消不可录", 1, null, 6,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(cancelled, playerId, 0);
        createEvent(admin, cancelled, goalJson(playerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long postponed = insertMatch("M06延期不可录", 1, null, 5,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(postponed, playerId, 0);
        createEvent(admin, postponed, goalJson(playerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        mockMvc.perform(get("/api/v1/c/matches/999999/events"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void partialUpdateMergesEffectiveEventAndAllowsClearingOptionalFields() throws Exception {
        Cookie player = playerLogin("13900002619", "事件合并更新");
        Cookie teammate = playerLogin("13900002620", "事件合并队友");
        Long playerId = playerId("13900002619");
        Long teammateId = playerId("13900002620");
        Long matchId = insertMatch("M06合并更新", 0, 0, 3,
                Instant.now().minus(Duration.ofHours(1)), Instant.now().plus(Duration.ofHours(1)));
        register(matchId, playerId, 0);
        register(matchId, teammateId, 0);

        Long eventId = JsonPath.<Number>read(createEvent(player, matchId, """
                {"type":0,"team":0,"playerId":%d,"assistPlayerId":%d,
                 "minute":12,"half":0,"penalty":true}
                """.formatted(playerId, teammateId))
                .andReturn().getResponse().getContentAsString(), "$.data.event.id").longValue();

        updateEvent(player, eventId, """
                {"assistPlayerId":null,"penalty":false,"version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.event.assistPlayer").value(nullValue()))
                .andExpect(jsonPath("$.data.event.penalty").value(false))
                .andExpect(jsonPath("$.data.event.minute").value(12))
                .andExpect(jsonPath("$.data.event.player.id").value(playerId));
    }

    private org.springframework.test.web.servlet.ResultActions createEvent(Cookie cookie, Long matchId,
                                                                          String body) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/events")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions updateEvent(Cookie cookie, Long eventId,
                                                                          String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/c/events/" + eventId)
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
        return cLogin(phone, "Player123!");
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
