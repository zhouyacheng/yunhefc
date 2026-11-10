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
}
