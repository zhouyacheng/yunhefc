package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class WechatAuthIT extends IntegrationTestSupport {

    @Test
    void authorizeCallbackBindPhoneAndReplayProtection() throws Exception {
        MvcResult authorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "/matches/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizeUrl").isNotEmpty())
                .andReturn();
        String authorizeUrl = JsonPath.read(authorize.getResponse().getContentAsString(), "$.data.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.indexOf("state=") + 6);
        Cookie authorizeCookie = cookie(authorize, "YHFC_C");

        MvcResult callback = mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(authorizeCookie)
                        .param("code", "abc")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(true))
                .andReturn();
        Cookie cookie = authorizeCookie;
        String csrf = JsonPath.read(callback.getResponse().getContentAsString(), "$.data.csrfToken");

        mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(authorizeCookie)
                        .param("code", "abc")
                        .param("state", state))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000041","purpose":"BIND"}
                                """))
                .andExpect(status().isOk());

        MvcResult bound = mockMvc.perform(post("/api/v1/c/auth/wechat/bind-phone")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000041","code":"123456","name":"微信球员","position":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.status").value(0))
                .andExpect(jsonPath("$.data.user.wechatBound").value(true))
                .andExpect(jsonPath("$.data.user.openid").doesNotExist())
                .andExpect(jsonPath("$.data.player.type").value(0))
                .andExpect(jsonPath("$.data.permissions").doesNotExist())
                .andReturn();
        assertThat(JsonPath.<String>read(bound.getResponse().getContentAsString(), "$.data.csrfToken")).isNotBlank();

        MvcResult secondAuthorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andReturn();
        String secondUrl = JsonPath.read(secondAuthorize.getResponse().getContentAsString(), "$.data.authorizeUrl");
        String secondState = secondUrl.substring(secondUrl.indexOf("state=") + 6);
        Cookie secondAuthorizeCookie = cookie(secondAuthorize, "YHFC_C");
        mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(secondAuthorizeCookie)
                        .param("code", "abc")
                        .param("state", secondState))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(false));

        mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "https://evil.example"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wechatBoundReflectsBindingAndAdminUnbindInBothResponses() throws Exception {
        Cookie admin = adminLogin("13900000981");
        String phone = "13900000982";
        jdbc.update("INSERT INTO app_user (phone, password_hash, openid, status) VALUES (?, ?, ?, 1)",
                phone, passwordEncoder.encode("Passw0rd!"), "mock-openid-bound");
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, skill)
                VALUES (1, ?, '微信绑定状态', 1, 1, 3)
                """, userId);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = 'PLAYER'", Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);

        MvcResult login = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.wechatBound").value(true))
                .andExpect(jsonPath("$.data.user.openid").doesNotExist())
                .andReturn();
        Cookie session = cookie(login, "YHFC_C");

        mockMvc.perform(get("/api/v1/c/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.wechatBound").value(true))
                .andExpect(jsonPath("$.data.user.openid").doesNotExist());

        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("name", "微信绑定状态"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(userId))
                .andExpect(jsonPath("$.data.items[0].wechatBound").value(true))
                .andExpect(jsonPath("$.data.items[0].openid").doesNotExist());

        mockMvc.perform(delete("/api/v1/b/users/" + userId + "/wechat-binding")
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT openid FROM app_user WHERE id = ?", String.class, userId)).isNull();
        mockMvc.perform(get("/api/v1/c/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.wechatBound").value(false));
        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("name", "微信绑定状态"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].wechatBound").value(false));
    }

    @Test
    void wechatLoginClearsPasswordLoginFailures() throws Exception {
        String phone = "13900000992";
        jdbc.update("INSERT INTO app_user (phone, password_hash, openid, status) VALUES (?, ?, ?, 1)",
                phone, passwordEncoder.encode("Passw0rd!"), "mock-openid-wechat-login");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/c/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"%s","password":"Wrong123!"}
                                    """.formatted(phone)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40102));
        }

        MvcResult authorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andReturn();
        String authorizeUrl = JsonPath.read(authorize.getResponse().getContentAsString(), "$.data.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.indexOf("state=") + 6);
        mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(cookie(authorize, "YHFC_C"))
                        .param("code", "wechat-login")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(false));

        mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }

    private Cookie adminLogin(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Admin123!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = 'ADMIN'", Long.class);
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
