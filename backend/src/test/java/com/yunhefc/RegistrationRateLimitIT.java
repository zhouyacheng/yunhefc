package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunhefc.common.ratelimit.RedisRateLimiter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class RegistrationRateLimitIT extends IntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String IP_A = "203.0.113.10";
    private static final String IP_B = "203.0.113.11";

    @MockitoSpyBean
    RedisRateLimiter rateLimiter;

    @Test
    void exceedingWindowReturnsRateLimitedWithRetryAfter() throws Exception {
        Cookie admin = bAdminLogin("13900000810");
        Cookie player = playerLogin("13900000811", "限流窗口");
        Long matchId = createMatch(admin, "M04限流窗口", "2099-07-01T20:00:00");
        setCapacities(matchId, 2, 1, 1);

        // 前 10 次同一幂等键：首次报名成功，其余为重放，全部计入限流窗口
        String idemKey = UUID.randomUUID().toString();
        for (int i = 0; i < 10; i++) {
            signup(player, matchId, idemKey, IP_A).andExpect(status().isOk());
        }

        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000811'", Long.class);
        Long ttl = redisTemplate.getExpire(
                "yunhefc:rate:registration:" + userId + ":" + IP_A, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(50L, 60L);

        MvcResult limited = signup(player, matchId, UUID.randomUUID().toString(), IP_A)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber())
                .andExpect(header().exists("Retry-After"))
                .andReturn();

        int retryAfterSeconds = JsonPath.read(
                limited.getResponse().getContentAsString(), "$.data.retryAfterSeconds");
        assertThat(retryAfterSeconds).isBetween(1, 60);
        assertThat(limited.getResponse().getHeader("Retry-After"))
                .isEqualTo(String.valueOf(retryAfterSeconds));

        // 窗口按账号 + IP 共享，与具体比赛无关
        Long anotherMatch = createMatch(admin, "M04限流窗口二", "2099-07-04T20:00:00");
        signup(player, anotherMatch, UUID.randomUUID().toString(), IP_A)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }

    @Test
    void limitWindowsAreIsolatedByAccountAndClientIp() throws Exception {
        Cookie admin = bAdminLogin("13900000812");
        Cookie playerA = playerLogin("13900000813", "限流甲");
        Cookie playerB = playerLogin("13900000814", "限流乙");
        Long matchId = createMatch(admin, "M04限流隔离", "2099-07-02T20:00:00");
        setCapacities(matchId, 2, 1, 1);
        // 未开放报名，失败请求同样计数：放行时统一返回 40905
        jdbc.update("UPDATE team_match SET status = 1 WHERE id = ?", matchId);

        for (int i = 0; i < 10; i++) {
            signup(playerA, matchId, UUID.randomUUID().toString(), IP_A)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(40905));
        }
        signup(playerA, matchId, UUID.randomUUID().toString(), IP_A)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));

        signup(playerA, matchId, UUID.randomUUID().toString(), IP_B)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
        signup(playerB, matchId, UUID.randomUUID().toString(), IP_A)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));
    }

    @Test
    void redisFailureDoesNotBlockSignup() throws Exception {
        Cookie admin = bAdminLogin("13900000815");
        Cookie player = playerLogin("13900000816", "限流降级");
        Long matchId = createMatch(admin, "M04限流降级", "2099-07-03T20:00:00");
        setCapacities(matchId, 2, 1, 1);

        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(rateLimiter).check(anyString(), anyInt(), any());

        signup(player, matchId, UUID.randomUUID().toString(), IP_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registration.status").value(0));

        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000816'", Long.class);
        verify(rateLimiter).check("registration:" + userId + ":" + IP_A, 10,
                Duration.ofSeconds(60));
    }

    private ResultActions signup(Cookie cookie, Long matchId, String idempotencyKey, String ip)
            throws Exception {
        return mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/registrations")
                .cookie(cookie)
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header("Idempotency-Key", idempotencyKey));
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
        return JsonPath.<Number>read(
                result.getResponse().getContentAsString(), "$.data.id").longValue();
    }

    private String local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant().toString();
    }

    private Cookie playerLogin(String phone, String name) throws Exception {
        Long userId = insertUser(phone, "PLAYER");
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, 1, 1, '[]', 3)
                """, userId, name);
        return cLogin(phone);
    }

    private Cookie bAdminLogin(String phone) throws Exception {
        insertUser(phone, "ADMIN");
        return bLogin(phone);
    }

    private Long insertUser(String phone, String roleCode) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, roleCode);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private Cookie cLogin(String phone) throws Exception {
        return login("/api/v1/c/auth/login", phone, "Player123!", "YHFC_C");
    }

    private Cookie bLogin(String phone) throws Exception {
        return login("/api/v1/b/auth/login", phone, "Admin123!", "YHFC_B");
    }

    private Cookie login(String path, String phone, String password, String cookieName)
            throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult result = mockMvc.perform(post(path)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, cookieName);
    }
}
