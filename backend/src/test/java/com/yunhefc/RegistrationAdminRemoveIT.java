package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class RegistrationAdminRemoveIT extends IntegrationTestSupport {

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
    void removingConfirmedRegistrationPromotesEarliestWaitlistedAndAudits() throws Exception {
        Cookie admin = bAdminLogin("13900001201");
        Cookie confirmed = playerLogin("13900001202", "移除确认");
        Cookie earlier = playerLogin("13900001203", "递补早");
        Cookie later = playerLogin("13900001204", "递补晚");
        Long matchId = createMatch(admin, "M04移除递补", "2099-08-01T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(earlier, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(later, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        Long registrationId = registrationId(matchId, "13900001202");

        remove(admin, registrationId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.removed").value(true))
                .andExpect(jsonPath("$.data.promoted").value(true))
                .andExpect(jsonPath("$.data.promotedPlayerId").value(playerId("13900001203")));

        Long adminId = userId("13900001201");
        assertThat(statusOf(matchId, "13900001202")).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT cancelled_by FROM registration
                WHERE match_id = ? AND player_id = ? AND status = 3
                """, Long.class, matchId, playerId("13900001202"))).isEqualTo(adminId);
        assertThat(jdbc.queryForObject("""
                SELECT cancelled_at FROM registration
                WHERE match_id = ? AND player_id = ? AND status = 3
                """, Object.class, matchId, playerId("13900001202"))).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT is_deleted FROM registration WHERE id = ?
                """, Integer.class, registrationId)).isZero();
        assertThat(statusOf(matchId, "13900001203")).isEqualTo(0);
        assertThat(statusOf(matchId, "13900001204")).isEqualTo(1);

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) AS action,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetType')) AS target_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.beforeJson')) AS before_json,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) AS after_json
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, registrationId);
        assertThat(audit.get("action")).isEqualTo("REGISTRATION_REMOVE");
        assertThat(audit.get("target_type")).isEqualTo("REGISTRATION");
        assertThat((String) audit.get("before_json")).contains("\"status\":0");
        assertThat((String) audit.get("after_json"))
                .contains("\"status\":3",
                        "\"promoted\":true",
                        "\"promotedPlayerId\":" + playerId("13900001203"));
    }

    @Test
    void removingWaitlistedRegistrationDoesNotPromote() throws Exception {
        Cookie admin = bAdminLogin("13900001205");
        Cookie confirmed = playerLogin("13900001206", "候补外确认");
        Cookie waitlisted = playerLogin("13900001207", "移除候补");
        Cookie next = playerLogin("13900001208", "候补次位");
        Long matchId = createMatch(admin, "M04移除候补", "2099-08-02T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(next, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        remove(admin, registrationId(matchId, "13900001207"), UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true))
                .andExpect(jsonPath("$.data.promoted").value(false))
                .andExpect(jsonPath("$.data.promotedPlayerId").doesNotExist());

        assertThat(statusOf(matchId, "13900001207")).isEqualTo(3);
        assertThat(statusOf(matchId, "13900001206")).isEqualTo(0);
        assertThat(statusOf(matchId, "13900001208")).isEqualTo(1);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isEqualTo(1);
    }

    @Test
    void removingTerminalRegistrationIsRejected() throws Exception {
        Cookie admin = bAdminLogin("13900001209");
        Cookie player = playerLogin("13900001210", "终态报名");
        Long matchId = createMatch(admin, "M04终态报名", "2099-08-03T20:00:00");
        setCapacities(matchId, 2, 1, 2);
        signup(player, matchId).andExpect(status().isOk());
        Long registrationId = registrationId(matchId, "13900001210");

        for (int terminal : List.of(2, 3)) {
            jdbc.update("UPDATE registration SET status = ? WHERE id = ?", terminal, registrationId);
            remove(admin, registrationId, UUID.randomUUID().toString())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM registration WHERE id = ?", Integer.class, registrationId))
                    .isEqualTo(terminal);
        }
    }

    @Test
    void removingRegistrationFromTerminalMatchIsRejected() throws Exception {
        Cookie admin = bAdminLogin("13900001211");
        Cookie player = playerLogin("13900001212", "终态比赛");
        for (int matchStatus : List.of(4, 6)) {
            Long matchId = createMatch(admin, "M04终态比赛" + matchStatus, "2099-08-04T20:00:00");
            setCapacities(matchId, 2, 1, 2);
            signup(player, matchId).andExpect(status().isOk());
            jdbc.update("UPDATE team_match SET status = ?, manual_status = 1 WHERE id = ?",
                    matchStatus, matchId);

            Long registrationId = registrationId(matchId, "13900001212");
            remove(admin, registrationId, UUID.randomUUID().toString())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM registration WHERE id = ?", Integer.class, registrationId))
                    .isEqualTo(0);
        }
    }

    @Test
    void removingMissingOrCrossTeamRegistrationIsNotFound() throws Exception {
        Cookie admin = bAdminLogin("13900001213");
        Cookie player = playerLogin("13900001214", "跨队移除");
        Long matchId = createMatch(admin, "M04跨队移除", "2099-08-05T20:00:00");
        signup(player, matchId).andExpect(status().isOk());
        Long registrationId = registrationId(matchId, "13900001214");

        remove(admin, 999999999L, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        jdbc.update("UPDATE team_match SET team_id = 2 WHERE id = ?", matchId);
        remove(admin, registrationId, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM registration WHERE id = ?", Integer.class, registrationId))
                .isEqualTo(0);
    }

    @Test
    void removalEnforcesPermissionAndIdempotency() throws Exception {
        Cookie admin = bAdminLogin("13900001215");
        Cookie confirmed = playerLogin("13900001216", "移除幂等确认");
        Cookie waitlisted = playerLogin("13900001217", "移除幂等候补");
        Long matchId = createMatch(admin, "M04移除幂等", "2099-08-06T20:00:00");
        setCapacities(matchId, 1, 1, 2);
        signup(confirmed, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        Long registrationId = registrationId(matchId, "13900001216");

        Long viewerRole = insertRole("M04_REMOVE_VIEWER", "attendance:view:any");
        Cookie viewer = insertBAndLogin("13900001218", viewerRole, "Viewer123!");
        remove(viewer, registrationId, UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(delete("/api/v1/b/registrations/" + registrationId)
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        String key = registrationId + "-remove-replay";
        remove(admin, registrationId, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true))
                .andExpect(jsonPath("$.data.promoted").value(true));
        remove(admin, registrationId, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.removed").value(true))
                .andExpect(jsonPath("$.data.promoted").value(true))
                .andExpect(jsonPath("$.data.promotedPlayerId").value(playerId("13900001217")));

        assertThat(countStatus(matchId, 3)).isEqualTo(1);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'REGISTRATION_REMOVE'
                """, Integer.class, registrationId)).isEqualTo(1);
    }

    @Test
    void concurrentRemovesOfSameRegistrationRemoveAndPromoteOnce() throws Exception {
        Cookie admin = bAdminLogin("13900001219");
        Cookie confirmed = playerLogin("13900001220", "并发移除确认");
        Cookie waitlisted = playerLogin("13900001221", "并发移除候补");
        Long matchId = createMatch(admin, "M04并发移除", "2099-08-07T20:00:00");
        setCapacities(matchId, 1, 1, 2);
        signup(confirmed, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        Long registrationId = registrationId(matchId, "13900001220");

        List<Integer> statuses = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return remove(admin, registrationId, UUID.randomUUID().toString()).andReturn();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<MvcResult> result : results) {
                statuses.add(result.get().getResponse().getStatus());
            }
        } finally {
            executor.shutdownNow();
        }

        // 后到者拿到比赛锁后以当前读复核到终态，返回 40905，不再重复移除/递补。
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(countStatus(matchId, 3)).isEqualTo(1);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isZero();
        assertThat(statusOf(matchId, "13900001221")).isEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'REGISTRATION_REMOVE'
                """, Integer.class, registrationId)).isEqualTo(1);
    }

    private ResultActions remove(Cookie cookie, Long registrationId, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(delete("/api/v1/b/registrations/" + registrationId)
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey));
    }

    private ResultActions signup(Cookie cookie, Long matchId) throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                .cookie(cookie)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private Long registrationId(Long matchId, String phone) {
        return jdbc.queryForObject("""
                SELECT r.id FROM registration r
                JOIN player p ON p.id = r.player_id
                JOIN app_user u ON u.id = p.user_id
                WHERE r.match_id = ? AND u.phone = ? AND r.is_deleted = 0
                ORDER BY r.id DESC LIMIT 1
                """, Long.class, matchId, phone);
    }

    private int statusOf(Long matchId, String phone) {
        return jdbc.queryForObject("""
                SELECT r.status FROM registration r
                JOIN player p ON p.id = r.player_id
                JOIN app_user u ON u.id = p.user_id
                WHERE r.match_id = ? AND u.phone = ? AND r.is_deleted = 0
                ORDER BY r.id DESC LIMIT 1
                """, Integer.class, matchId, phone);
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

    private Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Cookie insertBAndLogin(String phone, Long roleId, String password) throws Exception {
        insertUser(phone, 1, roleId);
        return bLogin(phone, password);
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
        Long userId = userId(phone);
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
