package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SessionIsolationIT extends IntegrationTestSupport {

    @Test
    void cAndBSessionsUseSeparateCookiesAndCannotCrossAccess() throws Exception {
        Cookie cCookie = register("13900000011");
        assertThat(cCookie.getPath()).isEqualTo("/api/v1/c");
        assertThat(cCookie.getMaxAge()).isEqualTo(7 * 24 * 60 * 60);
        assertThat(cCookie.isHttpOnly()).isTrue();

        Cookie bCookie = adminLogin("13900000012");
        assertThat(bCookie.getPath()).isEqualTo("/api/v1/b");
        assertThat(bCookie.getMaxAge()).isEqualTo(2 * 60 * 60);
        assertThat(bCookie.isHttpOnly()).isTrue();

        mockMvc.perform(get("/api/v1/c/me").cookie(cCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/b/me").cookie(bCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/c/me").cookie(bCookie)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/b/me").cookie(cCookie)).andExpect(status().isUnauthorized());
    }

    private Cookie register(String phone) throws Exception {
        sms(phone);
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"会话测试","position":2}
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

    private void sms(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }
}
