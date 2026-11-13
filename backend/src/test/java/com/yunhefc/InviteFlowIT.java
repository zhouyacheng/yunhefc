package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class InviteFlowIT extends IntegrationTestSupport {

    @Test
    void teamInviteActivatesPendingUserAndIsIdempotent() throws Exception {
        Cookie admin = adminLogin("13900000061");
        MvcResult invite = mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "invite-flow-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":0}"))
                .andExpect(status().isOk())
                .andReturn();
        String url = JsonPath.read(invite.getResponse().getContentAsString(), "$.data.inviteUrl");
        assertThat(url).matches("^.*/invite/[0-9a-f]{32}$");
        String token = url.substring(url.lastIndexOf('/') + 1);

        Cookie player = register("13900000062");
        mockMvc.perform(post("/api/v1/c/invites/" + token + "/accept").cookie(player).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.player.type").value(1));
        mockMvc.perform(post("/api/v1/c/invites/" + token + "/accept").cookie(player).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/c/me").cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.status").value(1))
                .andExpect(jsonPath("$.data.permissions[?(@ == 'attendance:view:any')]").exists());
        assertThat(jdbc.queryForObject("""
                SELECT used_count FROM invite_link
                WHERE token_hash = SHA2(?, 256)
                """, Integer.class, token)).isEqualTo(1);

        Long inviteId = jdbc.queryForObject(
                "SELECT id FROM invite_link WHERE token_hash = SHA2(?, 256)", Long.class, token);
        mockMvc.perform(post("/api/v1/b/invites/" + inviteId + "/revoke")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "invite-revoke-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        Cookie another = register("13900000063");
        mockMvc.perform(post("/api/v1/c/invites/" + token + "/accept").cookie(another).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40910));
    }

    private Cookie register(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"邀请流程","position":2}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
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
