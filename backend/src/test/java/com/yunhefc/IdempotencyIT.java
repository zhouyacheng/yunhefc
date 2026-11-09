package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class IdempotencyIT extends IntegrationTestSupport {

    @Test
    void repeatedKeyReplaysFirstResultAndRejectsDifferentBody() throws Exception {
        Cookie admin = adminLogin("13900000031");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000031'", Long.class);

        MvcResult first = mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-invite-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").isNotEmpty())
                .andReturn();

        MvcResult replay = mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-invite-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isOk())
                .andReturn();
        String firstUrl = JsonPath.read(first.getResponse().getContentAsString(), "$.data.inviteUrl");
        String replayUrl = JsonPath.read(replay.getResponse().getContentAsString(), "$.data.inviteUrl");
        assertThat(replayUrl).isEqualTo(firstUrl);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM invite_link WHERE inviter_user_id = ?", Integer.class, adminId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-invite-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0,\"expiresInDays\":8}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void staleInProgressPlaceholderIsReclaimedAfterGracePeriod() throws Exception {
        Cookie admin = adminLogin("13900000032");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000032'", Long.class);

        mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-stale-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isOk());

        jdbc.update("""
                UPDATE idempotency_key
                SET response_body = NULL, response_status = NULL, created_at = ?
                WHERE user_id = ? AND endpoint = 'INV-05' AND idem_key = 'idem-stale-1'
                """, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(300)), adminId);

        mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-stale-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").isNotEmpty());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM invite_link WHERE inviter_user_id = ?", Integer.class, adminId))
                .isEqualTo(2);

        jdbc.update("""
                UPDATE idempotency_key
                SET response_body = NULL, response_status = NULL, created_at = ?
                WHERE user_id = ? AND endpoint = 'INV-05' AND idem_key = 'idem-stale-1'
                """, java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(10)), adminId);

        mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "idem-stale-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40915));
    }

    private Cookie adminLogin(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Admin123!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Admin123!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }
}
