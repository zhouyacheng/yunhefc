package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class MiniProgramAuthIT extends IntegrationTestSupport {

    @Test
    void firstLoginRequiresPhoneBindingThenLogsInDirectly() throws Exception {
        MiniLoginTicket pending = miniLogin("dev-1");

        sendBindSmsCode("13900000051");
        MvcResult bound = bindPhone(pending, """
                {"bindTicket":"%s","phone":"13900000051","code":"123456","name":"小程序球员","position":2}
                """.formatted(pending.ticket()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.status").value(0))
                .andExpect(jsonPath("$.data.user.wechatBound").value(true))
                .andExpect(jsonPath("$.data.player.type").value(0))
                .andReturn();

        String body = bound.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.data.csrfToken")).isNotBlank();
        assertThat(body).doesNotContain("openid").doesNotContain("session_key");
        assertThat(jdbc.queryForObject(
                "SELECT last_login_at FROM app_user WHERE phone = '13900000051'", Object.class)).isNotNull();

        mockMvc.perform(get("/api/v1/c/me").cookie(cookie(bound, "YHFC_C")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.wechatBound").value(true));

        MvcResult relogin = mockMvc.perform(post("/api/v1/c/auth/mini/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"dev-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(false))
                .andExpect(jsonPath("$.data.bindTicket").value(nullValue()))
                .andExpect(jsonPath("$.data.user.wechatBound").value(true))
                .andReturn();
        assertThat(JsonPath.<String>read(relogin.getResponse().getContentAsString(), "$.data.csrfToken"))
                .isNotBlank();
    }

    @Test
    void existingPhoneBindsToExistingAccount() throws Exception {
        jdbc.update("INSERT INTO app_user (id, phone, status) VALUES (?, ?, ?)", 9001L, "13900000052", 1);

        MiniLoginTicket pending = miniLogin("existing-1");
        sendBindSmsCode("13900000052");

        bindPhone(pending, """
                {"bindTicket":"%s","phone":"13900000052","code":"123456"}
                """.formatted(pending.ticket()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(9001))
                .andExpect(jsonPath("$.data.user.wechatBound").value(true));

        assertThat(jdbc.queryForObject(
                "SELECT mp_openid FROM app_user WHERE id = 9001", String.class))
                .isEqualTo("mock-openid-existing-1");
    }

    @Test
    void disabledAccountIsRejected() throws Exception {
        jdbc.update("INSERT INTO app_user (id, phone, status) VALUES (?, ?, ?)", 9002L, "13900000053", 2);

        MiniLoginTicket pending = miniLogin("disabled-1");
        sendBindSmsCode("13900000053");

        bindPhone(pending, """
                {"bindTicket":"%s","phone":"13900000053","code":"123456"}
                """.formatted(pending.ticket()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40104));
    }

    @Test
    void replayedBindTicketIsRejected() throws Exception {
        MiniLoginTicket pending = miniLogin("replay-1");
        sendBindSmsCode("13900000054");

        String payload = """
                {"bindTicket":"%s","phone":"13900000054","code":"123456","name":"重放","position":1}
                """.formatted(pending.ticket());
        bindPhone(pending, payload).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/auth/mini/bind-phone")
                        .cookie(pending.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void bindWithoutTheLoginSessionIsRejected() throws Exception {
        MiniLoginTicket pending = miniLogin("no-session");

        mockMvc.perform(post("/api/v1/c/auth/mini/bind-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindTicket":"%s","phone":"13900000057","code":"123456","name":"无会话","position":1}
                                """.formatted(pending.ticket())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void accountAlreadyBoundToAnotherMiniProgramIsRejected() throws Exception {
        jdbc.update("INSERT INTO app_user (id, phone, mp_openid, status) VALUES (?, ?, ?, ?)",
                9003L, "13900000055", "mock-openid-other", 1);

        MiniLoginTicket pending = miniLogin("conflict-1");
        sendBindSmsCode("13900000055");

        bindPhone(pending, """
                {"bindTicket":"%s","phone":"13900000055","code":"123456"}
                """.formatted(pending.ticket()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void newPhoneRequiresNameAndPosition() throws Exception {
        MiniLoginTicket pending = miniLogin("missing-name");
        sendBindSmsCode("13900000056");

        bindPhone(pending, """
                {"bindTicket":"%s","phone":"13900000056","code":"123456"}
                """.formatted(pending.ticket()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/v1/c/auth/mini/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"rate-" + i + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/c/auth/mini/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"rate-over\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901));
    }


    private ResultActions bindPhone(MiniLoginTicket pending, String payload) throws Exception {
        return mockMvc.perform(post("/api/v1/c/auth/mini/bind-phone")
                .cookie(pending.cookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }
}
