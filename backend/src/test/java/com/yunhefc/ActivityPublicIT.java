package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.activity.application.ActivityApplicationService;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupRequest;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.lock.RedisLock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class ActivityPublicIT extends ActivityITSupport {

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private ActivityApplicationService activityService;

    @Test
    void listAppliesScopeFiltersAndSort() throws Exception {
        Long pastId = insertActivity("历史聚餐", pastAt(30, 18), 0, 10, pastAt(30, 17), 1);
        Long futureId = insertActivity("未来聚餐", futureAt(30, 18), 0, 10, futureAt(30, 17), 1);
        Long latestId = insertActivity("最近已结束", futureAt(31, 18), 2, 10, futureAt(31, 17), 1);
        Long cancelledId = insertActivity("更早已取消", pastAt(60, 18), 3, 10, pastAt(60, 17), 1);
        insertActivity("他队活动", futureAt(90, 18), 0, 10, futureAt(90, 17), 2);

        mockMvc.perform(get("/api/v1/c/activities").param("scope", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(latestId))
                .andExpect(jsonPath("$.data.items[0].status").value(2));

        mockMvc.perform(get("/api/v1/c/activities").param("scope", "latest").param("status", "3"))
                .andExpect(jsonPath("$.data.items[0].id").value(cancelledId));

        mockMvc.perform(get("/api/v1/c/activities").param("scope", "history"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value(pastId))
                .andExpect(jsonPath("$.data.items[1].id").value(cancelledId));

        mockMvc.perform(get("/api/v1/c/activities"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items[0].id").value(latestId))
                .andExpect(jsonPath("$.data.items[1].id").value(futureId))
                .andExpect(jsonPath("$.data.items[2].id").value(pastId))
                .andExpect(jsonPath("$.data.items[3].id").value(cancelledId));

        mockMvc.perform(get("/api/v1/c/activities").param("sort", "startAt:asc"))
                .andExpect(jsonPath("$.data.items[0].id").value(cancelledId))
                .andExpect(jsonPath("$.data.items[1].id").value(pastId))
                .andExpect(jsonPath("$.data.items[2].id").value(futureId))
                .andExpect(jsonPath("$.data.items[3].id").value(latestId));

        mockMvc.perform(get("/api/v1/c/activities").param("type", "0").param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/v1/c/activities").param("pageSize", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/c/activities").param("scope", "upcoming"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/c/activities").param("sort", "id:asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/c/activities").param("type", "9"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/c/activities").param("status", "9"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/c/activities").param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailExcludesRosterAndReturnsMySignup() throws Exception {
        Long activityId = insertActivity("详情活动", futureAt(30, 18), 0, 1,
                futureAt(30, 17), 1);
        createPlayerUser("13900002101", "详情报名者", 811, 1);
        Long playerId = playerId("13900002101");
        jdbc.update("""
                INSERT INTO activity_signup (activity_id, player_id, with_family, family_note, status)
                VALUES (?, ?, 1, '一位家属', 0)
                """, activityId, playerId);
        Cookie player = cLogin("13900002101", "Passw0rd!");

        mockMvc.perform(get("/api/v1/c/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signupCount").value(1))
                .andExpect(jsonPath("$.data.signupOpen").value(false))
                .andExpect(jsonPath("$.data.signups").doesNotExist())
                .andExpect(jsonPath("$.data.mySignup").value((Object) null));

        mockMvc.perform(get("/api/v1/c/activities/" + activityId).cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mySignup.playerId").value(playerId))
                .andExpect(jsonPath("$.data.mySignup.withFamily").value(true))
                .andExpect(jsonPath("$.data.mySignup.familyNote").value("一位家属"));

        mockMvc.perform(get("/api/v1/c/activities/" + activityId + "/signups"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/c/activities/" + activityId + "/signups").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].playerName").value("详情报名者"))
                .andExpect(jsonPath("$.data.items[0].playerNo").value(811))
                .andExpect(jsonPath("$.data.items[0].status").value(0))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("CONFIRMED"));
    }

    @Test
    void signupRejectsDuplicateFullClosedAndPastDeadline() throws Exception {
        Long activityId = insertActivity("报名活动", futureAt(30, 18), 0, 1,
                futureAt(30, 17), 1);
        createPlayerUser("13900002111", "报名甲", 812, 1);
        createPlayerUser("13900002112", "报名乙", 813, 1);
        Cookie first = cLogin("13900002111", "Passw0rd!");
        Cookie second = cLogin("13900002112", "Passw0rd!");

        mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(first).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":true,\"familyNote\":\"带孩子\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.signup.status").value(0))
                .andExpect(jsonPath("$.data.signup.playerName").value("报名甲"))
                .andExpect(jsonPath("$.data.signup.withFamily").value(true));

        mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(first).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40916));

        mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(second).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40918));

        mockMvc.perform(delete("/api/v1/c/activities/" + activityId + "/signups/me")
                        .cookie(first).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(true));
        mockMvc.perform(delete("/api/v1/c/activities/" + activityId + "/signups/me")
                        .cookie(first).with(csrf())
                        .header("Idempotency-Key", idemKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelled").value(false));

        mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(second).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isOk());

        jdbc.update("UPDATE team_activity SET status = 1 WHERE id = ?", activityId);
        mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(first).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40917));

        Long pastId = insertActivity("截止活动", futureAt(30, 18), 0, 10,
                pastAt(60, 17), 1);
        mockMvc.perform(post("/api/v1/c/activities/" + pastId + "/signups")
                        .cookie(first).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40917));
    }

    @Test
    void pendingAccountCanReadPublicActivities() throws Exception {
        Long activityId = insertActivity("待审核可读", futureAt(30, 18), 0, 10,
                futureAt(30, 17), 1);
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 0)", "13900002141");
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                userId("13900002141"), roleId("GUEST"));
        Cookie pending = cLogin("13900002141", "Passw0rd!");

        mockMvc.perform(get("/api/v1/c/activities").cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/api/v1/c/activities/" + activityId).cookie(pending))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(activityId));
        mockMvc.perform(get("/api/v1/c/activities/" + activityId + "/signups").cookie(pending))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
    }

    @Test
    void guestTypePlayerCannotSignup() throws Exception {
        Long activityId = insertActivity("外挂禁报", futureAt(30, 18), 0, 10,
                futureAt(30, 17), 1);
        createPlayerUser("13900002121", "外挂球员", 814, 0);
        Cookie guest = cLogin("13900002121", "Passw0rd!");

        mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                        .cookie(guest).with(csrf())
                        .header("Idempotency-Key", idemKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withFamily\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void signupReturnsBusyWhenLockIsHeld() throws Exception {
        Long activityId = insertActivity("锁活动", futureAt(30, 18), 0, 10,
                futureAt(30, 17), 1);
        createPlayerUser("13900002131", "锁球员", 815, 1);
        Cookie player = cLogin("13900002131", "Passw0rd!");
        String key = RedisLock.KEY_PREFIX + "activity:" + activityId + ":signup";
        String token = redisLock.tryLock(key, Duration.ofSeconds(30));
        try {
            mockMvc.perform(post("/api/v1/c/activities/" + activityId + "/signups")
                            .cookie(player).with(csrf())
                            .header("Idempotency-Key", idemKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"withFamily\":false}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40919));
        } finally {
            redisLock.unlock(key, token);
        }
    }

    @Test
    void concurrentSignupsNeverOversell() throws Exception {
        Long activityId = insertActivity("并发活动", futureAt(30, 18), 0, 1,
                futureAt(30, 17), 1);
        // 预热事务、连接池与锁路径，避免冷启动耗时把并发请求推到重试上限。
        Long warmupActivityId = insertActivity("预热活动", futureAt(31, 18), 0, 10,
                futureAt(31, 17), 1);
        createPlayerUser("13900002299", "预热球员", 819, 1);
        SecurityContextHolder.getContext()
                .setAuthentication(authenticated(userId("13900002299")));
        try {
            activityService.signup(warmupActivityId, new ActivitySignupRequest(false, null));
        } finally {
            SecurityContextHolder.clearContext();
        }

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String phone = "139000022%02d".formatted(i);
            createPlayerUser(phone, "并发球员" + i, 820 + i, 1);
            userIds.add(userId(phone));
        }

        ExecutorService executor = Executors.newFixedThreadPool(userIds.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> results = new ArrayList<>();
        for (int index = 0; index < userIds.size(); index++) {
            Long userId = userIds.get(index);
            int delayMillis = index * 20;
            results.add(executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(authenticated(userId));
                start.await();
                // 20ms 错峰模拟真实请求到达：完全同时到达会耗尽 3×50ms 重试预算并得到 40919。
                Thread.sleep(delayMillis);
                try {
                    activityService.signup(activityId, new ActivitySignupRequest(false, null));
                    return null;
                } catch (BusinessException ex) {
                    return ex.errorCode();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int success = 0;
        int full = 0;
        for (Future<ErrorCode> result : results) {
            ErrorCode code = result.get();
            if (code == null) {
                success++;
            } else {
                assertThat(code).isEqualTo(ErrorCode.ACTIVITY_FULL);
                full++;
            }
        }
        assertThat(success).isEqualTo(1);
        assertThat(full).isEqualTo(9);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_signup WHERE activity_id = ? AND status = 0",
                Integer.class, activityId)).isEqualTo(1);
    }

    private static UsernamePasswordAuthenticationToken authenticated(Long userId) {
        AuthPrincipal principal = new AuthPrincipal(
                userId, "13900000000", 1, 1L, "并发球员",
                Set.of("PLAYER"), Set.of("activity:signup:own", "activity:view:any"));
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("activity:signup:own")));
    }

}
