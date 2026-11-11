package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * M08 分组（GRP-01~06）：权限、状态门、候选过滤、评分落库、覆盖确认、手动调整、
 * 保存快照、清除与名单差异。
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

    @Test
    void adjustValidatesPartitionAndFingerprint() throws Exception {
        Cookie admin = adminLogin("13900002901", "分组调整");
        Long matchId = createMatch("M08调整赛", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002902", "调整门将", 1, 0, 4);
        Long p2 = insertPlayer("13900002903", "调整后卫", 1, 1, 3);
        Long p3 = insertPlayer("13900002904", "调整中场", 1, 2, 3);
        Long p4 = insertPlayer("13900002905", "调整前锋", 1, 3, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);
        register(matchId, p3, 1, 0);
        register(matchId, p4, 1, 0);
        generate(admin, matchId, "{}", key()).andExpect(status().isOk());

        String fingerprint = fingerprint(matchId);
        List<Long> originalA = teamIds(matchId, 0);
        List<Long> originalB = teamIds(matchId, 1);

        // 与现状相同是空操作：保留 saved_at、不写审计。
        adjust(admin, matchId, adjustBody(fingerprint, originalA, originalB, false), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grouping.savedAt").value(nullValue()));
        assertThat(auditCount("grouping.adjust")).isZero();

        Long moving = originalA.get(0);
        Long movingRowId = jdbc.queryForObject("""
                SELECT id FROM match_grouping_player
                WHERE grouping_id = ? AND player_id = ?
                """, Long.class, groupingId(matchId), moving);
        BigDecimal scoreBefore = jdbc.queryForObject("""
                SELECT final_score FROM match_grouping_player
                WHERE grouping_id = ? AND player_id = ?
                """, BigDecimal.class, groupingId(matchId), moving);
        List<Long> newA = new ArrayList<>(originalA);
        newA.remove(moving);
        List<Long> newB = new ArrayList<>(originalB);
        newB.add(moving);

        adjust(admin, matchId, adjustBody(fingerprint, newA, newB, false), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grouping.savedAt").value(nullValue()));
        assertThat(teamIds(matchId, 0)).isEqualTo(newA);
        assertThat(teamIds(matchId, 1)).isEqualTo(newB.stream().sorted().toList());
        Map<String, Object> movedRow = jdbc.queryForMap("""
                SELECT id, final_score FROM match_grouping_player
                WHERE grouping_id = ? AND player_id = ?
                """, groupingId(matchId), moving);
        assertThat(((Number) movedRow.get("id")).longValue()).isEqualTo(movingRowId);
        assertThat((BigDecimal) movedRow.get("final_score")).isEqualByComparingTo(scoreBefore);
        assertThat(auditCount("grouping.adjust")).isEqualTo(1);

        List<Long> duplicatedB = new ArrayList<>(newB);
        duplicatedB.add(newA.get(0));
        adjust(admin, matchId, adjustBody(fingerprint, newA, duplicatedB, false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        adjust(admin, matchId, adjustBody(fingerprint, newA, newB.subList(1, newB.size()), false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        List<Long> unknownB = new ArrayList<>(newB);
        unknownB.add(999_999L);
        adjust(admin, matchId, adjustBody(fingerprint, newA, unknownB, false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        adjust(admin, matchId, adjustBody("deadbeef", newA, newB, false), key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40911));

        adjust(admin, matchId, "{\"teams\":{\"A\":[],\"B\":[]}}", key())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        adjust(admin, matchId, "{\"expectedFingerprint\":\"" + fingerprint + "\"}", key())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Long p5 = insertPlayer("13900002906", "调整新增", 1, 1, 3);
        register(matchId, p5, 1, 0);
        adjust(admin, matchId, adjustBody(fingerprint, newA, newB, false), key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40911));

        Long external = createMatch("M08调整对外赛", 0, 2, 1, SEASON);
        adjust(admin, external, adjustBody(fingerprint, List.of(p1), List.of(p2), false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        Long noGrouping = createMatch("M08调整无分组", 1, 2, 1, SEASON);
        register(noGrouping, p1, 1, 0);
        register(noGrouping, p2, 1, 0);
        adjust(admin, noGrouping, adjustBody(RosterFingerprint.of(List.of(p1, p2)),
                List.of(p1), List.of(p2), false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        jdbc.update("UPDATE team_match SET status = 0 WHERE id = ?", matchId);
        adjust(admin, matchId, adjustBody(fingerprint, newA, newB, false), key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
    }

    @Test
    void savedAdjustmentRequiresConfirmationAndReturnsToDraft() throws Exception {
        Cookie admin = adminLogin("13900002911", "分组保存调整");
        Long matchId = createMatch("M08保存后调整", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002912", "保存门将", 1, 0, 4);
        Long p2 = insertPlayer("13900002913", "保存后卫", 1, 1, 3);
        Long p3 = insertPlayer("13900002914", "保存中场", 1, 2, 3);
        Long p4 = insertPlayer("13900002915", "保存前锋", 1, 3, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);
        register(matchId, p3, 1, 0);
        register(matchId, p4, 1, 0);
        generate(admin, matchId, "{}", key()).andExpect(status().isOk());
        String fingerprint = fingerprint(matchId);

        save(admin, matchId, saveBody(fingerprint, true), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedAt").exists())
                .andExpect(jsonPath("$.data.snapshot.rosterFingerprint").value(fingerprint));
        assertThat(jdbc.queryForObject("SELECT saved_at FROM match_grouping WHERE match_id = ?",
                Timestamp.class, matchId)).isNotNull();

        List<Long> teamA = teamIds(matchId, 0);
        List<Long> teamB = teamIds(matchId, 1);
        Long moving = teamA.get(0);
        List<Long> newA = new ArrayList<>(teamA);
        newA.remove(moving);
        List<Long> newB = new ArrayList<>(teamB);
        newB.add(moving);

        adjust(admin, matchId, adjustBody(fingerprint, newA, newB, false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42202));
        adjust(admin, matchId, adjustBody(fingerprint, newA, newB, true), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grouping.savedAt").value(nullValue()));
        assertThat(jdbc.queryForObject("SELECT saved_at FROM match_grouping WHERE match_id = ?",
                Timestamp.class, matchId)).isNull();
        assertThat(auditCount("grouping.adjust")).isEqualTo(1);

        save(admin, matchId, saveBody(fingerprint, true), key()).andExpect(status().isOk());
        Timestamp savedAt = jdbc.queryForObject(
                "SELECT saved_at FROM match_grouping WHERE match_id = ?", Timestamp.class, matchId);
        int adjustAudits = auditCount("grouping.adjust");

        adjust(admin, matchId, adjustBody(fingerprint, newA, newB, false), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grouping.savedAt").exists());
        assertThat(jdbc.queryForObject("SELECT saved_at FROM match_grouping WHERE match_id = ?",
                Timestamp.class, matchId)).isEqualTo(savedAt);
        assertThat(auditCount("grouping.adjust")).isEqualTo(adjustAudits);
    }

    @Test
    void saveRequiresConfirmationForWarningsAndRosterChange() throws Exception {
        Cookie admin = adminLogin("13900002921", "分组保存");
        Long matchId = createMatch("M08保存赛", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002922", "保存后卫一", 1, 1, 3);
        Long p2 = insertPlayer("13900002923", "保存后卫二", 1, 1, 3);
        Long p3 = insertPlayer("13900002924", "保存后卫三", 1, 1, 3);
        Long p4 = insertPlayer("13900002925", "保存后卫四", 1, 1, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);
        register(matchId, p3, 1, 0);
        register(matchId, p4, 1, 0);
        generate(admin, matchId, "{}", key()).andExpect(status().isOk());
        String fingerprint = fingerprint(matchId);

        save(admin, matchId, saveBody(fingerprint, false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42202));
        save(admin, matchId, saveBody(fingerprint, true), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.rosterFingerprint").value(fingerprint));
        assertThat(auditCount("grouping.save")).isEqualTo(1);

        Long p5 = insertPlayer("13900002926", "保存新增", 1, 1, 3);
        register(matchId, p5, 1, 0);
        save(admin, matchId, saveBody("deadbeef", true), key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40911));
        save(admin, matchId, saveBody(fingerprint, false), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42202));
        save(admin, matchId, saveBody(fingerprint, true), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.rosterFingerprint").value(fingerprint));
        assertThat(jdbc.queryForObject(
                "SELECT roster_fingerprint FROM match_grouping WHERE match_id = ?",
                String.class, matchId)).isEqualTo(fingerprint);
        assertThat(auditCount("grouping.save")).isEqualTo(2);

        jdbc.update("UPDATE match_grouping SET saved_at = '2020-01-01 00:00:00' WHERE match_id = ?",
                matchId);
        save(admin, matchId, saveBody(fingerprint, true), key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedAt").exists());
        assertThat(jdbc.queryForObject(
                "SELECT saved_at FROM match_grouping WHERE match_id = ?", Timestamp.class, matchId))
                .isAfter(Timestamp.valueOf("2020-01-01 00:00:00"));
        assertThat(auditCount("grouping.save")).isEqualTo(3);

        Long noGrouping = createMatch("M08保存无分组", 1, 2, 1, SEASON);
        register(noGrouping, p1, 1, 0);
        register(noGrouping, p2, 1, 0);
        save(admin, noGrouping, saveBody(RosterFingerprint.of(List.of(p1, p2)), true), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        jdbc.update("UPDATE team_match SET status = 0 WHERE id = ?", matchId);
        save(admin, matchId, saveBody(fingerprint, true), key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long external = createMatch("M08保存对外赛", 0, 2, 1, SEASON);
        save(admin, external, saveBody(fingerprint, true), key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void clearPhysicallyDeletesGroupingAndIsIdempotent() throws Exception {
        Cookie admin = adminLogin("13900002931", "分组清除");
        Long matchId = createMatch("M08清除赛", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002932", "清除门将", 1, 0, 4);
        Long p2 = insertPlayer("13900002933", "清除后卫", 1, 1, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);
        generate(admin, matchId, "{}", key()).andExpect(status().isOk());
        Long groupingId = groupingId(matchId);

        clear(admin, matchId, key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true));
        assertThat(groupingCount(matchId)).isZero();
        assertThat(groupingPlayerCount(groupingId)).isZero();
        assertThat(auditCount("grouping.clear")).isEqualTo(1);

        clear(admin, matchId, key())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true));
        assertThat(auditCount("grouping.clear")).isEqualTo(1);

        generate(admin, matchId, "{}", key()).andExpect(status().isOk());
        assertThat(groupingPlayerCount(groupingId(matchId))).isEqualTo(2);

        Cookie readOnly = backendLogin("13900002933");
        clear(readOnly, matchId, key())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        jdbc.update("UPDATE team_match SET status = 0 WHERE id = ?", matchId);
        clear(admin, matchId, key())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        Long external = createMatch("M08清除对外赛", 0, 2, 1, SEASON);
        clear(admin, external, key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void concurrentAdjustmentsSerializeOnTheMatchRow() throws Exception {
        Cookie admin = adminLogin("13900002941", "分组并发");
        Long matchId = createMatch("M08并发赛", 1, 2, 1, SEASON);
        Long p1 = insertPlayer("13900002942", "并发门将", 1, 0, 4);
        Long p2 = insertPlayer("13900002943", "并发后卫", 1, 1, 3);
        Long p3 = insertPlayer("13900002944", "并发中场", 1, 2, 3);
        Long p4 = insertPlayer("13900002945", "并发前锋", 1, 3, 3);
        register(matchId, p1, 1, 0);
        register(matchId, p2, 1, 0);
        register(matchId, p3, 1, 0);
        register(matchId, p4, 1, 0);
        generate(admin, matchId, "{}", key()).andExpect(status().isOk());
        String fingerprint = fingerprint(matchId);
        List<Long> teamA = teamIds(matchId, 0);
        List<Long> teamB = teamIds(matchId, 1);
        Long movingToB = teamA.get(0);
        Long movingToA = teamB.get(0);
        List<Long> alternateA = new ArrayList<>(teamA);
        alternateA.remove(movingToB);
        List<Long> alternateB = new ArrayList<>(teamB);
        alternateB.add(movingToB);
        List<Long> alternateA2 = new ArrayList<>(teamA);
        alternateA2.add(movingToA);
        List<Long> alternateB2 = new ArrayList<>(teamB);
        alternateB2.remove(movingToA);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(
                    () -> runAdjust(ready, start, admin, matchId,
                            adjustBody(fingerprint, alternateA, alternateB, false)));
            Future<Integer> second = pool.submit(
                    () -> runAdjust(ready, start, admin, matchId,
                            adjustBody(fingerprint, alternateA2, alternateB2, false)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        assertThat(groupingPlayerCount(groupingId(matchId))).isEqualTo(4);
        assertThat(auditCount("grouping.adjust")).isEqualTo(2);
        // 行锁串行化后最终状态必须是某一次完整划分，不能是交错结果。
        assertThat(List.of(
                alternateA.stream().sorted().toList(),
                alternateA2.stream().sorted().toList()))
                .contains(teamIds(matchId, 0));
    }

    private int runAdjust(CountDownLatch ready, CountDownLatch start, Cookie cookie,
                          Long matchId, String body) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return adjust(cookie, matchId, body, key()).andReturn().getResponse().getStatus();
    }

    private ResultActions adjust(Cookie cookie, Long matchId, String body, String key)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/b/matches/" + matchId + "/grouping")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions save(Cookie cookie, Long matchId, String body, String key)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/grouping/save")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions clear(Cookie cookie, Long matchId, String key) throws Exception {
        return mockMvc.perform(delete("/api/v1/b/matches/" + matchId + "/grouping")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", key));
    }

    private static String adjustBody(String fingerprint, List<Long> teamA, List<Long> teamB,
                                     boolean confirm) {
        return """
                {"teams":{"A":%s,"B":%s},"expectedFingerprint":"%s","confirm":%s}
                """.formatted(teamA, teamB, fingerprint, confirm);
    }

    private static String saveBody(String fingerprint, boolean confirm) {
        return """
                {"expectedFingerprint":"%s","confirm":%s}
                """.formatted(fingerprint, confirm);
    }

    private String fingerprint(Long matchId) {
        return jdbc.queryForObject(
                "SELECT roster_fingerprint FROM match_grouping WHERE match_id = ?",
                String.class, matchId);
    }

    private List<Long> teamIds(Long matchId, int team) {
        return jdbc.queryForList("""
                SELECT p.player_id FROM match_grouping_player p
                JOIN match_grouping g ON g.id = p.grouping_id
                WHERE g.match_id = ? AND p.team = ?
                ORDER BY p.player_id
                """, Long.class, matchId, team);
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
