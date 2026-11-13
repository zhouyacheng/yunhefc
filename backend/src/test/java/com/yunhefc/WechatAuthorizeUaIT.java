package com.yunhefc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "yunhefc.wechat.require-wechat-ua=true")
class WechatAuthorizeUaIT extends IntegrationTestSupport {

    @Test
    void rejectsNonWechatBrowserAndAllowsWechatBrowser() throws Exception {
        mockMvc.perform(get("/api/v1/c/auth/wechat/authorize").param("redirect", "/"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .header("User-Agent", "Mozilla/5.0 MicroMessenger/8.0")
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorizeUrl").isNotEmpty());
    }
}
