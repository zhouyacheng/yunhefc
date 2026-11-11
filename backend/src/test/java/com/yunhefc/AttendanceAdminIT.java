package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

class AttendanceAdminIT extends IntegrationTestSupport {

    @Test
    void formalPlayerAndAdminSeeWholeTeamAttendanceWithSourceLabels() throws Exception {
        Cookie admin = bAdminLogin("13900003501");
        Cookie player = playerLogin("13900003502", "考勤名单正式");
        Long guestPlayer = guestPlayer("13900003503", "考勤名单外挂");
        Long second = playerId("13900003502");
        Long tempPlayer = playerLoginOnly("13900003504", "考勤临时到场", 1);

        Long matchId = insertMatch("M05考勤名单", 2);
        register(matchId, second, 1, 0);
        register(matchId, guestPlayer, 0, 0);
        Long presentId = insertAttendance(matchId, second, 0, 0, "2099-09-01 10:00:00");
        Long absentId = insertAttendance(matchId, guestPlayer, 1, 2, "2099-09-01 10:00:01");
        Long tempId = insertAttendance(matchId, tempPlayer, 2, 1, "2099-09-01 10:00:02");

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(presentId))
                .andExpect(jsonPath("$.data.items[0].player.id").value(second))
                .andExpect(jsonPath("$.data.items[0].player.name").value("考勤名单正式"))
                .andExpect(jsonPath("$.data.items[0].player.jersey").value(502))
                .andExpect(jsonPath("$.data.items[0].player.position").value(2))
                .andExpect(jsonPath("$.data.items[0].player.positionLabel").value("MIDFIELDER"))
                .andExpect(jsonPath("$.data.items[0].status").value(0))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("PRESENT"))
                .andExpect(jsonPath("$.data.items[0].source").value(0))
                .andExpect(jsonPath("$.data.items[0].sourceLabel").value("SELF"))
                .andExpect(jsonPath("$.data.items[1].id").value(absentId))
                .andExpect(jsonPath("$.data.items[1].statusLabel").value("ABSENT"))
                .andExpect(jsonPath("$.data.items[1].sourceLabel").value("SYSTEM"))
                .andExpect(jsonPath("$.data.items[2].id").value(tempId))
                .andExpect(jsonPath("$.data.items[2].statusLabel").value("TEMP_PRESENT"))
                .andExpect(jsonPath("$.data.items[2].sourceLabel").value("ADMIN"));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance")
                        .param("status", "2").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(tempId));

        mockMvc.perform(get("/api/v1/b/matches/" + matchId + "/attendance").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance")
                        .param("status", "9").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance")
                        .param("pageSize", "30").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance")
                        .param("page", "0").cookie(player))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Cookie guest = cLogin("13900003503", "Guest123!");
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance").cookie(guest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(get("/api/v1/c/matches/" + matchId + "/attendance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        Long crossTeamMatch = insertCrossTeamMatch();
        mockMvc.perform(get("/api/v1/c/matches/" + crossTeamMatch + "/attendance").cookie(player))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(get("/api/v1/b/matches/" + crossTeamMatch + "/attendance").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void adminMarkCreatesAndOverwritesAttendanceWithAudit() throws Exception {
        Cookie admin = bAdminLogin("13900003511");
        Long player = playerLoginOnly("13900003512", "考勤标记球员", 1);
        Long matchId = insertMatch("M05考勤标记", 2);
        register(matchId, player, 1, 0);

        MvcResult first = mockMvc.perform(put(
                                "/api/v1/b/matches/" + matchId + "/attendance/" + player)
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.attendance.playerId").value(player))
                .andExpect(jsonPath("$.data.attendance.status").value(0))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("PRESENT"))
                .andExpect(jsonPath("$.data.attendance.source").value(1))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("ADMIN"))
                .andExpect(jsonPath("$.data.attendance.recordedAt").exists())
                .andReturn();
        Long attendanceId = com.jayway.jsonpath.JsonPath.<Number>read(
                first.getResponse().getContentAsString(), "$.data.attendance.id").longValue();

        mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/" + player)
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(attendanceId))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("ABSENT"))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("ADMIN"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM attendance WHERE id = ?", Integer.class, attendanceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT recorded_by FROM attendance WHERE id = ?", Long.class, attendanceId))
                .isEqualTo(userId("13900003511"));

        assertThat(auditCount("ATTENDANCE_MARK", attendanceId)).isEqualTo(2);
        assertThat(auditBeforeField(attendanceId, 1, "$.status")).isNull();
        assertThat(auditAfterField(attendanceId, 1, "$.status")).isZero();
        assertThat(auditAfterField(attendanceId, 1, "$.source")).isEqualTo(1);
        assertThat(auditBeforeField(attendanceId, 2, "$.status")).isZero();
        assertThat(auditAfterField(attendanceId, 2, "$.status")).isEqualTo(1);

        mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/" + player)
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/" + player)
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/" + player)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Cookie formal = bLogin("13900003512", "Player123!");
        mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/" + player)
                        .cookie(formal)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/999999")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void markRejectsCancelledAndPostponedMatches() throws Exception {
        Cookie admin = bAdminLogin("13900003521");
        Long player = playerLoginOnly("13900003522", "考勤作废球员", 1);
        Long cancelled = insertMatch("M05考勤已取消", 6);
        Long postponed = insertMatch("M05考勤已延期", 5);

        for (Long matchId : new Long[]{cancelled, postponed}) {
            mockMvc.perform(put("/api/v1/b/matches/" + matchId + "/attendance/" + player)
                            .cookie(admin)
                            .with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":0}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance", Integer.class)).isZero();
    }

    @Test
    void tempPresentRequiresEligibleActiveTeamPlayerWithoutConfirmedRegistration() throws Exception {
        Cookie admin = bAdminLogin("13900003531");
        Long confirmed = playerLoginOnly("13900003532", "临时已确认", 1);
        Long waitlisted = playerLoginOnly("13900003533", "临时候补", 1);
        Long unregistered = playerLoginOnly("13900003534", "临时未报名", 1);
        Long cancelledReg = playerLoginOnly("13900003535", "临时已取消", 1);
        Long inactive = playerLoginOnly("13900003536", "临时已注销", 1);
        jdbc.update("UPDATE app_user SET status = 2 WHERE id = ?", userId("13900003536"));
        Long pending = playerLoginOnly("13900003538", "临时待审核", 1);
        jdbc.update("UPDATE app_user SET status = 0 WHERE id = ?", userId("13900003538"));
        Long crossTeam = insertPlayerForTeam(2, "13900003537", "临时跨队", 1);

        Long matchId = insertMatch("M05临时到场", 2);
        register(matchId, confirmed, 1, 0);
        register(matchId, waitlisted, 1, 1);
        register(matchId, cancelledReg, 1, 2);
        int registrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE match_id = ?", Integer.class, matchId);

        MvcResult waitlistedResult = mockMvc.perform(post(
                                "/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + waitlisted + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.status").value(2))
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("TEMP_PRESENT"))
                .andExpect(jsonPath("$.data.attendance.source").value(1))
                .andExpect(jsonPath("$.data.attendance.sourceLabel").value("ADMIN"))
                .andReturn();
        Long waitlistedAttendanceId = com.jayway.jsonpath.JsonPath.<Number>read(
                waitlistedResult.getResponse().getContentAsString(),
                "$.data.attendance.id").longValue();

        MvcResult unregisteredResult = mockMvc.perform(post(
                                "/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + unregistered + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Long unregisteredAttendanceId = com.jayway.jsonpath.JsonPath.<Number>read(
                unregisteredResult.getResponse().getContentAsString(),
                "$.data.attendance.id").longValue();

        // 重试返回同一临时到场记录，不重复建审计。
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + unregistered + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.id").value(unregisteredAttendanceId));

        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + confirmed + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + inactive + "}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + crossTeam + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 已取消报名的球员允许补录临时到场。
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + cancelledReg + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.statusLabel").value("TEMP_PRESENT"));

        // PENDING 账号未注销，仍可补录。
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + pending + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendance.status").value(2))
                .andExpect(jsonPath("$.data.attendance.source").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(registrationCount);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE match_id = ?", Integer.class, matchId))
                .isEqualTo(4);
        assertThat(auditCount("ATTENDANCE_TEMP", waitlistedAttendanceId)).isEqualTo(1);
        assertThat(auditCount("ATTENDANCE_TEMP", unregisteredAttendanceId)).isEqualTo(1);
        assertThat(auditAfterField(unregisteredAttendanceId, 1, "$.status")).isEqualTo(2);
        assertThat(auditAfterField(unregisteredAttendanceId, 1, "$.source")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        Cookie formal = bLogin("13900003532", "Player123!");
        mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                        .cookie(formal)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":" + unregistered + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void tempPresentRejectsCancelledAndPostponedMatches() throws Exception {
        Cookie admin = bAdminLogin("13900003541");
        Long player = playerLoginOnly("13900003542", "临时作废球员", 1);
        Long cancelled = insertMatch("M05临时已取消", 6);
        Long postponed = insertMatch("M05临时已延期", 5);

        for (Long matchId : new Long[]{cancelled, postponed}) {
            mockMvc.perform(post("/api/v1/b/matches/" + matchId + "/attendance/temp")
                            .cookie(admin)
                            .with(csrf())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"playerId\":" + player + "}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendance", Integer.class)).isZero();
    }

    private Long insertMatch(String title, int status) {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        long seasonNo = nextMatchNo();
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', ?, 1, 30, ?, ?, 1)
                """, title, java.sql.Timestamp.from(startAt),
                java.sql.Timestamp.from(startAt.plus(2, ChronoUnit.HOURS)),
                java.sql.Timestamp.from(startAt.minus(1, ChronoUnit.DAYS)),
                java.sql.Timestamp.from(startAt.minus(4, ChronoUnit.HOURS)),
                status, seasonNo, seasonNo);
        return jdbc.queryForObject("SELECT id FROM team_match WHERE title = ?", Long.class, title);
    }

    private Long insertCrossTeamMatch() {
        long seasonNo = nextMatchNo();
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by)
                VALUES (2, 'M05考勤跨队', 1, NOW(3) + INTERVAL 2 HOUR, NOW(3) + INTERVAL 4 HOUR,
                    NOW(3), NOW(3) + INTERVAL 10 HOUR, '测试场地', '2099', '橙队', '兰队',
                    2, 1, 30, ?, ?, 1)
                """, seasonNo, seasonNo);
        return jdbc.queryForObject(
                "SELECT id FROM team_match WHERE title = 'M05考勤跨队'", Long.class);
    }

    private long seasonNo = 900_000;

    private long nextMatchNo() {
        return seasonNo++;
    }

    private void register(Long matchId, Long playerId, int type, int status) {
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, ?, ?, NOW(3))
                """, matchId, playerId, type, status);
    }

    private Long insertAttendance(Long matchId, Long playerId, int status, int source,
                                  String recordedAt) {
        jdbc.update("""
                INSERT INTO attendance (match_id, player_id, status, source, recorded_by, recorded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, matchId, playerId, status, source, source == 1 ? 1 : null, recordedAt);
        return jdbc.queryForObject(
                "SELECT id FROM attendance WHERE match_id = ? AND player_id = ?",
                Long.class, matchId, playerId);
    }

    private int auditCount(String action, Long targetId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = ?
                """, Integer.class, targetId, action);
    }

    private Integer auditBeforeField(Long targetId, int occurrence, String snapshotPath) {
        // 首次标记时 before 为 NULL，快照路径必然取不到值。
        return jdbc.queryForObject("""
                SELECT JSON_EXTRACT(
                    JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.beforeJson')), ?)
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) IN
                      ('ATTENDANCE_MARK', 'ATTENDANCE_TEMP')
                ORDER BY id ASC LIMIT 1 OFFSET ?
                """, Integer.class, snapshotPath, targetId, occurrence - 1);
    }

    private Integer auditAfterField(Long targetId, int occurrence, String snapshotPath) {
        return jdbc.queryForObject("""
                SELECT JSON_EXTRACT(
                    JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')), ?)
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) IN
                      ('ATTENDANCE_MARK', 'ATTENDANCE_TEMP')
                ORDER BY id ASC LIMIT 1 OFFSET ?
                """, Integer.class, snapshotPath, targetId, occurrence - 1);
    }

    private Long guestPlayer(String phone, String name) {
        insertPlayer(phone, name, 0, null);
        return playerId(phone);
    }

    private Long playerLoginOnly(String phone, String name, int type) {
        insertPlayer(phone, name, type,
                type == 1 ? Integer.parseInt(phone.substring(phone.length() - 3)) : null);
        return playerId(phone);
    }

    private Long insertPlayerForTeam(long teamId, String phone, String name, int type) {
        Long role = roleId(type == 1 ? "PLAYER" : "GUEST");
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, role);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (?, ?, ?, ?, 2, NULL, '[]', 3)
                """, teamId, userId, name, type);
        return jdbc.queryForObject(
                "SELECT id FROM player WHERE user_id = ?", Long.class, userId);
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

    private void insertPlayer(String phone, String name, int type, Integer jersey) {
        Long userId = insertUser(phone, 1, roleId(type == 1 ? "PLAYER" : "GUEST"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, ?, 2, ?, '[]', 3)
                """, userId, name, type, jersey);
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        insertPlayer(phone, name, 1, Integer.parseInt(phone.substring(phone.length() - 3)));
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
