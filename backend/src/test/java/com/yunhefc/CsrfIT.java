package com.yunhefc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class CsrfIT extends IntegrationTestSupport {

    @Test
    void writeWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000021","purpose":"REGISTER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfTokenAllowsWriteAndSmsRateLimitApplies() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/c/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = cookie(csrf, "YHFC_C");
        String token = csrfToken(csrf);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                            .cookie(cookie)
                            .header("X-XSRF-TOKEN", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phone":"13900000022","purpose":"REGISTER"}
                                    """))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000022","purpose":"REGISTER"}
                                """))
                .andExpect(status().isTooManyRequests());
    }
}
