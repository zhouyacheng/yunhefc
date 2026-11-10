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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class RegistrationCancelIT extends IntegrationTestSupport {

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
    void confirmedCancelPromotesEarliestWaitlisted() throws Exception {
        Cookie admin = bAdminLogin("13900001101");
        Cookie first = playerLogin("13900001102", "取消球员甲");
        Cookie earlier = playerLogin("13900001103", "候补早");
        Cookie later = playerLogin("13900001104", "候补晚");
        Long matchId = createMatch(admin, "M04取消递补", "2099-07-01T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(first, matchId).andExpect(status().isOk());
        signup(earlier, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(later, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        cancel(first, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.promoted").value(true))
                .andExpect(jsonPath("$.data.promotedPlayerId").value(playerId("13900001103")));

        Long userId = userId("13900001102");
        assertThat(statusOf(matchId, "13900001102")).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT cancelled_by FROM registration
                WHERE match_id = ? AND player_id = ? AND status = 2
                """, Long.class, matchId, playerId("13900001102"))).isEqualTo(userId);
        assertThat(jdbc.queryForObject("""
                SELECT cancelled_at FROM registration
                WHERE match_id = ? AND player_id = ? AND status = 2
                """, Object.class, matchId, playerId("13900001102"))).isNotNull();
        assertThat(statusOf(matchId, "13900001103")).isEqualTo(0);
        assertThat(statusOf(matchId, "13900001104")).isEqualTo(1);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isEqualTo(1);
    }

    @Test
    void cancelBeforeLockWorksAfterSignupDeadline() throws Exception {
        Cookie admin = bAdminLogin("13900001105");
        Cookie player = playerLogin("13900001106", "截止后取消");
        Long matchId = createMatch(admin, "M04截止后取消", "2099-07-02T20:00:00");
        // 报名截止只停新增；锁定（当天 10:00）之前仍可取消。
        jdbc.update("UPDATE team_match SET status = 1, manual_status = 1 WHERE id = ?", matchId);
        signup(player, matchId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, 1, 0, '2026-11-01 08:00:00')
                """, matchId, playerId("13900001106"));

        cancel(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.promoted").value(false));
        assertThat(statusOf(matchId, "13900001106")).isEqualTo(2);
    }

    @Test
    void cancelIsRejectedAfterLockOrWhenMatchIsNotActive() throws Exception {
        Cookie admin = bAdminLogin("13900001107");
        Cookie player = playerLogin("13900001108", "取消校验");
        Long matchId = createMatch(admin, "M04取消校验", "2099-07-03T20:00:00");
        signup(player, matchId).andExpect(status().isOk());

        jdbc.update("UPDATE team_match SET cancel_lock = '2020-01-01 00:00:00' WHERE id = ?", matchId);
        cancel(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        // 管理员可提前锁定（cancelLock 仍在未来），锁定后球员不可自行取消。
        for (int status : List.of(2, 5, 6)) {
            Long other = createMatch(admin, "M04取消状态" + status, "2099-07-04T20:00:00");
            jdbc.update("""
                    INSERT INTO registration (match_id, player_id, type, status, registered_at)
                    VALUES (?, ?, 1, 0, '2026-11-01 08:00:00')
                    """, other, playerId("13900001108"));
            jdbc.update("UPDATE team_match SET status = ?, manual_status = 1 WHERE id = ?",
                    status, other);
            cancel(player, other, UUID.randomUUID().toString())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
            assertThat(statusOf(other, "13900001108")).isEqualTo(0);
        }

        Long unregistered = createMatch(admin, "M04未报名取消", "2099-07-05T20:00:00");
        cancel(player, unregistered, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        cancel(player, 999999999L, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void waitlistAndGuestCancelDoNotPromote() throws Exception {
        Cookie admin = bAdminLogin("13900001109");
        Cookie confirmed = playerLogin("13900001110", "正式确认");
        Cookie waitlisted = playerLogin("13900001111", "正式候补");
        Cookie guest = guestLogin("13900001112", "外挂确认");
        Long matchId = createMatch(admin, "M04取消不递补", "2099-07-06T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(guest, matchId).andExpect(jsonPath("$.data.registration.status").value(0));

        cancel(waitlisted, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.promoted").value(false))
                .andExpect(jsonPath("$.data.promotedPlayerId").doesNotExist());
        assertThat(statusOf(matchId, "13900001110")).isEqualTo(0);

        cancel(guest, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.promoted").value(false));
        assertThat(statusOf(matchId, "13900001111")).isEqualTo(2);
        assertThat(countStatus(matchId, 1)).isZero();
    }

    @Test
    void confirmedCancelWithoutWaitlistReportsNoPromotion() throws Exception {
        Cookie admin = bAdminLogin("13900001113");
        Cookie player = playerLogin("13900001114", "无候补取消");
        Long matchId = createMatch(admin, "M04无候补取消", "2099-07-07T20:00:00");
        setCapacities(matchId, 2, 1, 2);
        signup(player, matchId).andExpect(status().isOk());

        cancel(player, matchId, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.promoted").value(false))
                .andExpect(jsonPath("$.data.promotedPlayerId").doesNotExist());
        assertThat(statusOf(matchId, "13900001114")).isEqualTo(2);
    }

    @Test
    void concurrentCancelsPromoteAtMostOnce() throws Exception {
        Cookie admin = bAdminLogin("13900001115");
        Cookie confirmedA = playerLogin("13900001116", "并发取消甲");
        Cookie confirmedB = playerLogin("13900001117", "并发取消乙");
        Cookie waitlisted = playerLogin("13900001118", "并发候补");
        Long matchId = createMatch(admin, "M04并发取消", "2099-07-08T20:00:00");
        setCapacities(matchId, 2, 1, 2);

        signup(confirmedA, matchId).andExpect(status().isOk());
        signup(confirmedB, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        List<Cookie> cancellers = List.of(confirmedA, confirmedB);
        List<Integer> statuses = new ArrayList<>();
        int promoted = 0;
        ExecutorService executor = Executors.newFixedThreadPool(cancellers.size());
        CountDownLatch ready = new CountDownLatch(cancellers.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (Cookie cookie : cancellers) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return cancel(cookie, matchId, UUID.randomUUID().toString()).andReturn();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<MvcResult> result : results) {
                MvcResult response = result.get();
                statuses.add(response.getResponse().getStatus());
                promoted += JsonPath.<Boolean>read(
                        response.getResponse().getContentAsString(), "$.data.promoted") ? 1 : 0;
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statuses).containsOnly(200);
        assertThat(promoted).isEqualTo(1);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isZero();
        assertThat(countStatus(matchId, 2)).isEqualTo(2);
        assertThat(statusOf(matchId, "13900001118")).isEqualTo(0);
    }

    @Test
    void concurrentSignupAndCancelKeepQueueConsistent() throws Exception {
        Cookie admin = bAdminLogin("13900001119");
        Cookie confirmed = playerLogin("13900001120", "交错确认");
        Cookie waitlisted = playerLogin("13900001121", "交错候补");
        Cookie signingUp = playerLogin("13900001122", "交错报名");
        Long matchId = createMatch(admin, "M04交错报名取消", "2099-07-09T20:00:00");
        setCapacities(matchId, 1, 1, 2);

        signup(confirmed, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> cancelResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancel(confirmed, matchId, UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> signupResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return signup(signingUp, matchId).andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(cancelResult.get()).isEqualTo(200);
            assertThat(signupResult.get()).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        // 无论先后，名额既不能超卖，也不能出现「确认未满却有人滞留守候」。
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isEqualTo(1);
        assertThat(statusOf(matchId, "13900001121")).isEqualTo(0);
        assertThat(statusOf(matchId, "13900001122")).isEqualTo(1);
    }

    @Test
    void concurrentCancelAndDisableDoNotDeadlockOrStrandWaitlist() throws Exception {
        Cookie admin = bAdminLogin("13900001128");
        Cookie confirmed = playerLogin("13900001129", "并发递补确认");
        Cookie waitlisted = playerLogin("13900001130", "并发递补候补");
        Cookie nextWaitlisted = playerLogin("13900001131", "并发递补次候补");
        Long matchId = createMatch(admin, "M04取消注销交错", "2099-07-13T20:00:00");
        setCapacities(matchId, 1, 1, 2);
        signup(confirmed, matchId).andExpect(status().isOk());
        signup(waitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        signup(nextWaitlisted, matchId).andExpect(jsonPath("$.data.registration.status").value(1));
        Long waitlistedPlayerId = playerId("13900001130");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> cancelResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return cancel(confirmed, matchId, UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> disableResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return disable(admin, waitlistedPlayerId, UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(cancelResult.get()).isEqualTo(200);
            assertThat(disableResult.get()).isEqualTo(200);
        } finally {
            executor.shutdownNow();
        }

        // 两个操作都提交后：名额由下一候补递补，注销者与取消者都不再有效，且无人滞留守候。
        assertThat(statusOf(matchId, "13900001130")).isEqualTo(2);
        assertThat(statusOf(matchId, "13900001131")).isEqualTo(0);
        assertThat(countStatus(matchId, 0)).isEqualTo(1);
        assertThat(countStatus(matchId, 1)).isZero();
    }

    @Test
    void concurrentSignupAndDisableLeaveNoActiveRegistration() throws Exception {
        Cookie admin = bAdminLogin("13900001123");
        Cookie player = playerLogin("13900001124", "注销交错");
        Long firstMatch = createMatch(admin, "M04注销交错一", "2099-07-10T20:00:00");
        Long secondMatch = createMatch(admin, "M04注销交错二", "2099-07-11T20:00:00");
        setCapacities(firstMatch, 2, 1, 2);
        setCapacities(secondMatch, 2, 1, 2);
        signup(player, firstMatch).andExpect(status().isOk());
        Long playerId = playerId("13900001124");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> signupResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return signup(player, secondMatch).andReturn().getResponse().getStatus();
            });
            Future<Integer> disableResult = executor.submit(() -> {
                ready.countDown();
                start.await();
                return disable(admin, playerId, UUID.randomUUID().toString())
                        .andReturn().getResponse().getStatus();
            });
            ready.await();
            start.countDown();
            assertThat(disableResult.get()).isEqualTo(200);
            assertThat(signupResult.get()).isIn(200, 401, 403);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM registration
                WHERE player_id = ? AND status IN (0, 1) AND is_deleted = 0
                """, Integer.class, playerId)).isZero();
    }

    @Test
    void cancelEnforcesPermissionIdempotencyAndReplay() throws Exception {
        Cookie admin = bAdminLogin("13900001125");
        Cookie player = playerLogin("13900001126", "取消权限");
        Long matchId = createMatch(admin, "M04取消幂等", "2099-07-12T20:00:00");
        setCapacities(matchId, 2, 1, 2);
        signup(player, matchId).andExpect(status().isOk());

        Long viewerRole = insertRole("M04_CANCEL_VIEWER", "match:view:any");
        Cookie viewer = insertAndLogin("13900001127", viewerRole, "Viewer123!");
        cancel(viewer, matchId, UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(delete("/api/v1/c/matches/" + matchId + "/registrations/me")
                        .cookie(player)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        String key = matchId + "-cancel-replay";
        cancel(player, matchId, key).andExpect(status().isOk());
        cancel(player, matchId, key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true))
                .andExpect(jsonPath("$.data.promoted").value(false));
        assertThat(countStatus(matchId, 2)).isEqualTo(1);
    }

    private ResultActions cancel(Cookie cookie, Long matchId, String idempotencyKey) throws Exception {
        return mockMvc.perform(delete("/api/v1/c/matches/" + matchId + "/registrations/me")
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

    private ResultActions disable(Cookie actor, Long playerId, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/v1/b/players/" + playerId + "/disable")
                .cookie(actor)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"M04并发注销\"}")
                .header("Idempotency-Key", idempotencyKey));
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

    private Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Cookie insertAndLogin(String phone, Long roleId, String password) throws Exception {
        insertUser(phone, 1, roleId);
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
