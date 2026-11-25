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

import java.util.Set;

class AdminHandoffIT extends IntegrationTestSupport {

    private static final String PASSWORD = "Admin123!";

    @Test
    void issuesTicketAndExchangesForBackendSession() throws Exception {
        activeUser("13900001001", "ADMIN", 1);
        Cookie cCookie = cLogin("13900001001");

        MvcResult issued = issueTicket(cCookie);
        MvcResult exchanged = exchangeTicket(ticketOf(issued));

        Cookie bCookie = cookie(exchanged, "YHFC_B");
        assertThat(bCookie.getPath()).isEqualTo("/api/v1/b");
        assertThat(bCookie.getMaxAge()).isEqualTo(12 * 60 * 60);
        assertThat(bCookie.isHttpOnly()).isTrue();
        assertThat(exchanged.getResponse().getCookie("YHFC_C")).isNull();

        mockMvc.perform(get("/api/v1/b/me").cookie(bCookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/c/me").cookie(cCookie)).andExpect(status().isOk());
    }

    @Test
    void replayedTicketIsRejected() throws Exception {
        activeUser("13900001002", "ADMIN", 1);
        String ticket = ticketOf(issueTicket(cLogin("13900001002")));

        exchangeTicket(ticket);
        mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void invalidTicketIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("not-a-real-ticket")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void expiredTicketIsRejected() throws Exception {
        activeUser("13900001003", "ADMIN", 1);
        String ticket = ticketOf(issueTicket(cLogin("13900001003")));

        Set<String> keys = redisTemplate.keys("yunhefc:auth:admin-handoff:*");
        assertThat(keys).hasSize(1);
        redisTemplate.delete(keys);

        mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void guestWithoutBackendPermissionCannotIssueTicket() throws Exception {
        activeUser("13900001004", "GUEST", 1);
        mockMvc.perform(post("/api/v1/c/auth/admin-ticket")
                        .cookie(cLogin("13900001004"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void pendingAccountCannotIssueTicket() throws Exception {
        activeUser("13900001005", "GUEST", 0);
        mockMvc.perform(post("/api/v1/c/auth/admin-ticket")
                        .cookie(cLogin("13900001005"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));
    }

    @Test
    void exchangeRevalidatesPermissionAndBurnsTicket() throws Exception {
        activeUser("13900001006", "ADMIN", 1);
        String ticket = ticketOf(issueTicket(cLogin("13900001006")));
        jdbc.update("DELETE FROM user_role WHERE user_id = (SELECT id FROM app_user WHERE phone = ?)",
                "13900001006");
        Long guestRole = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'GUEST' AND team_id = 1",
                Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) "
                        + "SELECT id, ? FROM app_user WHERE phone = ?", guestRole, "13900001006");

        mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void exchangeWithoutCsrfIsRejected() throws Exception {
        activeUser("13900001007", "ADMIN", 1);
        String ticket = ticketOf(issueTicket(cLogin("13900001007")));

        mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ticketTtlIsSixtySeconds() throws Exception {
        activeUser("13900001008", "ADMIN", 1);
        issueTicket(cLogin("13900001008"));

        String key = redisTemplate.keys("yunhefc:auth:admin-handoff:*").iterator().next();
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isNotNull().isBetween(1L, 60L);
    }

    private MvcResult issueTicket(Cookie cCookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/admin-ticket")
                        .cookie(cCookie)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresIn").value(60))
                .andExpect(jsonPath("$.data.ticket")
                        .value(org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{43}")))
                .andReturn();
        return result;
    }

    private String ticketOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.ticket");
    }

    private MvcResult exchangeTicket(String ticket) throws Exception {
        return mockMvc.perform(post("/api/v1/b/auth/admin-ticket/exchange")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.csrfToken").isNotEmpty())
                .andReturn();
    }

    private String ticketBody(String ticket) {
        return """
                {"ticket":"%s"}
                """.formatted(ticket);
    }

    private void activeUser(String phone, String roleCode, int status) {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, ?)",
                phone, passwordEncoder.encode(PASSWORD), status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject("SELECT id FROM app_role WHERE code = ? AND team_id = 1",
                Long.class, roleCode);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private Cookie cLogin(String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }
}
