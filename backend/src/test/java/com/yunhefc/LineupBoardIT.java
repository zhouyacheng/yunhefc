package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * M07 阵容板（LIN-01~04）：默认值、保存与版本、终态、自定义/历史模板、对手方空槽位、
 * 队内分组约束与权限。
 */
class LineupBoardIT extends IntegrationTestSupport {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final int HOME = 0;
    private static final int AWAY = 1;

    @Test
    void unsavedLineupReturnsDefaultFormationAndEligibleBench() throws Exception {
        Cookie admin = adminLogin("13900004101", "阵容默认管理员");
        Long matchId = insertMatch("M07默认阵容", 1, 2, null, 1);
        insertPlayers(matchId, 10, 401);

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/lineups/0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        Cookie guest = playerLogin("13900004102", "阵容外挂", 0, 4402, 3);
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/lineups/0").cookie(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        getAdmin(admin, matchId, HOME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.side").value(0))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.formation.name").value("2-3-2"))
                .andExpect(jsonPath("$.data.formation.slots", hasSize(8)))
                .andExpect(jsonPath("$.data.starters", hasSize(8)))
                .andExpect(jsonPath("$.data.starters[0].slotKey").value("gk"))
                .andExpect(jsonPath("$.data.starters[0].player").value(nullValue()))
                .andExpect(jsonPath("$.data.bench", hasSize(10)));

        Cookie formal = cLogin(playerPhone(401), "Player123!");
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/lineups/0").cookie(formal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.side").value(0))
                .andExpect(jsonPath("$.data.formation.name").value("2-3-2"))
                .andExpect(jsonPath("$.data.starters", hasSize(8)))
                .andExpect(jsonPath("$.data.bench", hasSize(10)))
                .andExpect(jsonPath("$.data.version").doesNotExist());

        mockMvc.perform(get("/api/v1/b/matches/" + matchId + "/lineups/2").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void savesStartersAndBenchWithVersionAndWritesAudit() throws Exception {
        Cookie admin = adminLogin("13900004111", "阵容保存管理员");
        Long matchId = insertMatch("M07保存阵容", 1, 2, null, 1);
        List<Long> players = insertPlayers(matchId, 10, 411);
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        save(admin, matchId, HOME, templateId, slots,
                players.subList(0, 8), players.subList(8, 10), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        Long lineupId = lineupId(matchId, HOME);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM lineup WHERE id = ?", Integer.class, lineupId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lineup_slot WHERE lineup_id = ? AND slot_type = 0
                """, Integer.class, lineupId)).isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lineup_slot WHERE lineup_id = ? AND slot_type = 1
                """, Integer.class, lineupId)).isEqualTo(2);

        getAdmin(admin, matchId, HOME)
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.starters", hasSize(8)))
                .andExpect(jsonPath("$.data.starters[0].player.id").value(players.get(0)))
                .andExpect(jsonPath("$.data.bench", hasSize(2)))
                .andExpect(jsonPath("$.data.bench[0].id").value(players.get(8)))
                .andExpect(jsonPath("$.data.updatedBy").isNotEmpty());

        save(admin, matchId, HOME, templateId, slots,
                players.subList(0, 8), players.subList(8, 10), 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(1));

        List<Long> swapped = new ArrayList<>(players.subList(1, 9));
        save(admin, matchId, HOME, templateId, slots, swapped, List.of(players.get(0)), 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));

        assertThat(auditCount("LINEUP_SAVE", lineupId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lineup_slot WHERE lineup_id = ? AND player_id = ?
                """, Integer.class, lineupId, players.get(0))).isEqualTo(1);
    }

    @Test
    void rejectsTerminalAndPostponedMatches() throws Exception {
        Cookie admin = adminLogin("13900004121", "阵容状态管理员");
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        for (int status : List.of(4, 5, 6)) {
            Long matchId = insertMatch("M07终态" + status, 1, status, null, 1);
            List<Long> players = insertPlayers(matchId, 8, 420 + status);
            save(admin, matchId, HOME, templateId, slots, players, List.of(), 0)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM lineup WHERE match_id = ?", Integer.class, matchId))
                    .isZero();
        }

        Long progressMatch = insertMatch("M07进行中", 1, 3, null, 1);
        List<Long> players = insertPlayers(progressMatch, 8, 430);
        save(admin, progressMatch, HOME, templateId, slots, players, List.of(), 0)
                .andExpect(status().isOk());
    }

    @Test
    void customAndDeletedTemplatesRemainReadableForHistoricalLineup() throws Exception {
        Cookie admin = adminLogin("13900004131", "阵容模板管理员");
        Long matchId = insertMatch("M07历史模板", 1, 2, null, 1);
        List<Long> players = insertPlayers(matchId, 8, 431);
        Long customId = createTemplate(admin, presetId(), "历史阵容阵型");
        List<String> customSlots = slotKeys(customId);

        save(admin, matchId, HOME, customId, customSlots, players, List.of(), 0)
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/b/formation-templates/" + customId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        getAdmin(admin, matchId, HOME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formation.id").value(customId))
                .andExpect(jsonPath("$.data.formation.name").value("历史阵容阵型"))
                .andExpect(jsonPath("$.data.formation.slots", hasSize(8)))
                .andExpect(jsonPath("$.data.starters[0].player.id").value(players.get(0)));

        Cookie player = cLogin(playerPhone(431), "Player123!");
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/lineups/0").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formation.id").value(customId))
                .andExpect(jsonPath("$.data.formation.slots", hasSize(8)))
                .andExpect(jsonPath("$.data.starters[0].player.id").value(players.get(0)));
    }

    @Test
    void opponentSideRecordsOnlyFormationAndEmptySlots() throws Exception {
        Cookie admin = adminLogin("13900004141", "阵容对手管理员");
        Long matchId = insertMatch("M07对外比赛", 0, 2, HOME, 1);
        List<Long> players = insertPlayers(matchId, 8, 441);
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        save(admin, matchId, AWAY, templateId, slots, emptyPlayers(8), List.of(), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        getAdmin(admin, matchId, AWAY)
                .andExpect(jsonPath("$.data.side").value(1))
                .andExpect(jsonPath("$.data.formation.name").value("2-3-2"))
                .andExpect(jsonPath("$.data.starters", hasSize(8)))
                .andExpect(jsonPath("$.data.starters[0].player").value(nullValue()))
                .andExpect(jsonPath("$.data.bench", hasSize(0)));

        Cookie player = cLogin(playerPhone(441), "Player123!");
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/lineups/1").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starters[0].player").value(nullValue()))
                .andExpect(jsonPath("$.data.bench", hasSize(0)));

        List<Long> onePlayer = new ArrayList<>(emptyPlayers(8));
        onePlayer.set(0, players.get(0));
        save(admin, matchId, AWAY, templateId, slots, onePlayer, List.of(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        save(admin, matchId, AWAY, templateId, slots, emptyPlayers(8),
                List.of(players.get(0)), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        save(admin, matchId, HOME, templateId, slots, players, List.of(), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    void savedGroupingRestrictsEachSideToItsGroup() throws Exception {
        Cookie admin = adminLogin("13900004151", "阵容分组管理员");
        Long matchId = insertMatch("M07分组约束", 1, 2, null, 1);
        List<Long> teamA = insertPlayers(matchId, 8, 450);
        List<Long> teamB = insertPlayers(matchId, 8, 460);
        saveGrouping(matchId, teamA, teamB);
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        save(admin, matchId, HOME, templateId, slots, teamB, List.of(), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201))
                .andExpect(jsonPath("$.message", containsString("A")));

        save(admin, matchId, HOME, templateId, slots,
                new ArrayList<>(Stream.concat(teamA.stream().limit(7),
                        Stream.of(teamB.get(0))).toList()), List.of(), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        save(admin, matchId, HOME, templateId, slots,
                teamA.subList(0, 8), List.of(teamB.get(0)), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        save(admin, matchId, HOME, templateId, slots, teamA.subList(0, 8), List.of(), 0)
                .andExpect(status().isOk());
        save(admin, matchId, AWAY, templateId, slots, teamB.subList(0, 8), List.of(), 0)
                .andExpect(status().isOk());
    }

    @Test
    void rosterChangeOutsideSavedGroupingIsWarnedAndCannotBeArranged() throws Exception {
        Cookie admin = adminLogin("13900004165", "阵容名单变化管理员");
        Long matchId = insertMatch("M07名单变化", 1, 2, null, 1);
        List<Long> teamA = insertPlayers(matchId, 8, 475);
        saveGrouping(matchId, teamA, List.of());
        Long added = insertPlayer("13900004751", "名单新增球员", 1, 4751, 2);
        register(matchId, added, 1, 0);

        mockMvc.perform(get("/api/v1/b/matches/" + matchId + "/grouping")
                        .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rosterDiff.changed").value(true))
                .andExpect(jsonPath("$.data.rosterDiff.addedPlayerIds[0]").value(added));

        getAdmin(admin, matchId, HOME)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bench", hasSize(8)))
                .andExpect(jsonPath("$.data.bench[?(@.id == " + added + ")]", hasSize(0)));

        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);
        List<Long> withAdded = new ArrayList<>(teamA.subList(0, 7));
        withAdded.add(added);
        save(admin, matchId, HOME, templateId, slots, withAdded, List.of(), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void draftGroupingAndNoGroupingFallBackToConfirmedRoster() throws Exception {
        Cookie admin = adminLogin("13900004161", "阵容名单管理员");
        Long matchId = insertMatch("M07草稿分组", 1, 2, null, 1);
        List<Long> teamA = insertPlayers(matchId, 8, 470);
        List<Long> teamB = insertPlayers(matchId, 8, 480);
        insertGrouping(matchId, teamA, teamB, false);
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        // 草稿分组不约束。
        save(admin, matchId, HOME, templateId, slots, teamB.subList(0, 8), List.of(), 0)
                .andExpect(status().isOk());

        Long plainMatch = insertMatch("M07无分组", 1, 2, null, 1);
        List<Long> confirmed = insertPlayers(plainMatch, 8, 490);
        Long cancelled = insertPlayer("13900004951", "取消报名", 1, 4951, 2);
        register(plainMatch, cancelled, 1, 2);
        Long disabled = insertPlayer("13900004952", "注销账号", 1, 4952, 3);
        register(plainMatch, disabled, 1, 0);
        jdbc.update("UPDATE app_user SET status = 2 WHERE phone = '13900004952'");
        Long unregistered = insertPlayer("13900004953", "未报名", 1, 4953, 2);

        save(admin, plainMatch, HOME, templateId, slots, confirmed, List.of(), 0)
                .andExpect(status().isOk());
        save(admin, plainMatch, HOME, templateId, slots,
                new ArrayList<>(Stream.concat(confirmed.stream().limit(7),
                        Stream.of(unregistered)).toList()), List.of(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        save(admin, plainMatch, HOME, templateId, slots,
                new ArrayList<>(Stream.concat(confirmed.stream().limit(7),
                        Stream.of(cancelled)).toList()), List.of(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        save(admin, plainMatch, HOME, templateId, slots,
                new ArrayList<>(Stream.concat(confirmed.stream().limit(7),
                        Stream.of(disabled)).toList()), List.of(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void saveAsTemplateCopiesFormationWithoutTouchingLineupVersion() throws Exception {
        Cookie admin = adminLogin("13900004171", "阵容另存管理员");
        Long matchId = insertMatch("M07另存模板", 1, 2, null, 1);
        List<Long> players = insertPlayers(matchId, 8, 471);
        Long templateId = presetId();
        save(admin, matchId, HOME, templateId, slotKeys(templateId), players, List.of(), 0)
                .andExpect(status().isOk());

        MvcResult created = mockMvc.perform(post(
                        "/api/v1/b/matches/" + matchId + "/lineups/0/template")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  另存阵型  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long newTemplateId = JsonPath.<Number>read(
                created.getResponse().getContentAsString(), "$.data.templateId").longValue();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM formation_template
                WHERE id = ? AND team_id = 1 AND is_preset = 0 AND name = '另存阵型'
                """, Integer.class, newTemplateId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT base_template_id FROM formation_template WHERE id = ?", Long.class,
                newTemplateId)).isEqualTo(templateId);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formation_slot WHERE template_id = ?", Integer.class,
                newTemplateId)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM lineup WHERE match_id = ? AND side = 0", Integer.class,
                matchId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/lineups/0/template")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void lineupViolationsReturnValidationOrBusinessCodes() throws Exception {
        Cookie admin = adminLogin("13900004181", "阵容校验管理员");
        Long matchId = insertMatch("M07阵容校验", 1, 2, null, 1);
        List<Long> players = insertPlayers(matchId, 9, 481);
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        save(admin, matchId, HOME, templateId, slots,
                new ArrayList<>(Stream.concat(players.stream().limit(7),
                        Stream.of((Long) null)).toList()), List.of(), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        save(admin, matchId, HOME, templateId, slots.subList(0, 7), players.subList(0, 7),
                List.of(), 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        save(admin, matchId, HOME, templateId, slots, players.subList(0, 8),
                List.of(players.get(0)), 0)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        save(admin, matchId, HOME, 999_999L, slots, players.subList(0, 8), List.of(), 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        save(admin, matchId, HOME, templateId, slots, players.subList(0, 8), List.of(), 5)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data.currentVersion").value(0));
    }

    @Test
    void backendPermissionsSeparateViewAndEdit() throws Exception {
        Cookie admin = adminLogin("13900004191", "阵容权限管理员");
        Long matchId = insertMatch("M07权限赛", 1, 2, null, 1);
        insertPlayers(matchId, 8, 491);
        Long templateId = presetId();
        List<String> slots = slotKeys(templateId);

        insertUser("13900004921", roleId("PLAYER"));
        Cookie backendPlayer = backendLogin("13900004921");
        getAdmin(backendPlayer, matchId, HOME)
                .andExpect(status().isOk());
        save(backendPlayer, matchId, HOME, templateId, slots, emptyPlayers(8), List.of(), 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/lineups/0/template")
                        .cookie(backendPlayer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"无权模板"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        getAdmin(admin, matchId, HOME).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions getAdmin(Cookie cookie, Long matchId, int side) throws Exception {
        return mockMvc.perform(get("/api/v1/b/matches/" + matchId + "/lineups/" + side)
                .cookie(cookie));
    }

    private ResultActions save(Cookie cookie, Long matchId, int side, Long templateId,
                               List<String> slots, List<Long> players, List<Long> bench,
                               int version) throws Exception {
        return mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/lineups/" + side)
                .cookie(cookie).with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(templateId, slots, players, bench, version)));
    }

    private static String saveBody(Long templateId, List<String> slots, List<Long> players,
                                   List<Long> bench, int version) {
        String starters = IntStream.range(0, slots.size())
                .mapToObj(index -> "{\"slotKey\":\"%s\",\"playerId\":%s}"
                        .formatted(slots.get(index), players.get(index)))
                .collect(Collectors.joining(","));
        String benchJson = bench.stream().map(String::valueOf).collect(Collectors.joining(","));
        return """
                {"formationTemplateId":%d,"starters":[%s],"bench":[%s],"version":%d}
                """.formatted(templateId, starters, benchJson, version);
    }

    private static List<Long> emptyPlayers(int size) {
        List<Long> players = new ArrayList<>();
        IntStream.range(0, size).forEach(index -> players.add(null));
        return players;
    }

    private Long createTemplate(Cookie cookie, Long baseTemplateId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/formation-templates")
                        .cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseTemplateId":%d,"name":"%s"}
                                """.formatted(baseTemplateId, name)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.<Number>read(result.getResponse().getContentAsString(),
                "$.data.template.id").longValue();
    }

    private Long presetId() {
        return jdbc.queryForObject(
                "SELECT id FROM formation_template WHERE is_preset = 1 ORDER BY id LIMIT 1",
                Long.class);
    }

    private List<String> slotKeys(Long templateId) {
        return jdbc.queryForList("""
                SELECT slot_key FROM formation_slot WHERE template_id = ?
                ORDER BY sort_order, id
                """, String.class, templateId);
    }

    private Long lineupId(Long matchId, int side) {
        return jdbc.queryForObject(
                "SELECT id FROM lineup WHERE match_id = ? AND side = ?", Long.class, matchId, side);
    }

    private int auditCount(String action, Long targetId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                """, Integer.class, targetId, action);
    }

    private Long insertMatch(String title, int type, int status, Integer ourSide,
                             int includeGuests) {
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
                VALUES (1, ?, ?, ?, ?, ?, ?, '测试场地', '2099', ?, ?, ?, ?, 1, 30, ?, ?, ?, 1)
                """, title, type, Timestamp.from(startAt), Timestamp.from(endAt),
                Timestamp.from(signupDeadline), Timestamp.from(cancelLock),
                type == 0 ? "云河FC" : "橙队", type == 0 ? "对手队" : "兰队",
                ourSide, status, includeGuests, seasonNo, totalNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    /**
     * 批量创建正式球员并全部确认报名；手机号后缀从 base + 100 起，避免与其他固定号码冲突。
     */
    private List<Long> insertPlayers(Long matchId, int count, int base) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String phone = "139" + String.format("%08d", base * 1000 + index);
            Long playerId = insertPlayer(phone, "阵容球员" + base + "-" + index, 1,
                    5000 + base * 10 + index, index % 4);
            register(matchId, playerId, 1, 0);
            ids.add(playerId);
        }
        return ids;
    }

    private String playerPhone(int base) {
        return "139" + String.format("%08d", base * 1000);
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

    private Long insertPlayer(String phone, String name, int type, int jersey, int position) {
        Long userId = insertUser(phone, roleId(type == 1 ? "PLAYER" : "GUEST"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, ?, '[]', 3)
                """, userId, name, type, position, jersey);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Cookie playerLogin(String phone, String name, int type, int jersey, int position)
            throws Exception {
        insertPlayer(phone, name, type, jersey, position);
        return cLogin(phone, "Player123!");
    }

    private Cookie adminLogin(String phone, String name) throws Exception {
        insertUser(phone, roleId("ADMIN"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, 1, 0, ?, '[]', 3)
                """, userId(phone), name, 300 + Integer.parseInt(phone.substring(phone.length() - 2)));
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

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = userId(phone);
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
