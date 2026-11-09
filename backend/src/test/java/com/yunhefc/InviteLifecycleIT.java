package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunhefc.auth.application.InviteExpiryService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class InviteLifecycleIT extends IntegrationTestSupport {

    @Autowired
    InviteExpiryService inviteExpiryService;

    @Test
    void teamInviteReplacesGuestButKeepsCustomRoles() throws Exception {
        Cookie admin = adminLogin("13900000201");
        Long coachRoleId = insertCustomRole("COACH");
        Cookie guest = register("13900000202");

        Long userId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000202'", Long.class);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, coachRoleId);

        String token = createInvite(admin, "{\"type\":0}");

        mockMvc.perform(post("/api/v1/c/invites/" + token + "/accept")
                        .cookie(guest)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.player.type").value(1));

        assertThat(jdbc.queryForList("""
                SELECT r.code FROM user_role ur
                JOIN app_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND ur.is_deleted = 0 AND r.is_deleted = 0
                """, String.class, userId))
                .containsExactlyInAnyOrder("PLAYER", "COACH");
    }

    @Test
    void matchInviteIsConsumedOnceAcrossRegisterAndAccept() throws Exception {
        Cookie admin = adminLogin("13900000211");
        Long matchId = insertMatch(102);
        String token = createInvite(admin,
                "{\"type\":1,\"matchId\":" + matchId + ",\"maxUses\":1}");

        Cookie user = register("13900000212", token);
        assertThat(usedCount(token)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/c/invites/" + token + "/accept")
                        .cookie(user)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchId").value(matchId));

        assertThat(usedCount(token)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM invite_use
                WHERE invite_id = (SELECT id FROM invite_link WHERE token_hash = SHA2(?, 256))
                """, Integer.class, token)).isEqualTo(1);
    }

    @Test
    void bindPhoneForExistingAccountDoesNotRequirePosition() throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                "13900000221", passwordEncoder.encode("Passw0rd!"));

        MvcResult authorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andReturn();
        String authorizeUrl = JsonPath.read(authorize.getResponse().getContentAsString(), "$.data.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.indexOf("state=") + 6);
        Cookie authorizeCookie = cookie(authorize, "YHFC_C");
        MvcResult callback = mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(authorizeCookie)
                        .param("code", "bind-existing")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(true))
                .andReturn();
        Cookie cookie = authorizeCookie;
        String csrf = JsonPath.read(callback.getResponse().getContentAsString(), "$.data.csrfToken");

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000221","purpose":"BIND"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/auth/wechat/bind-phone")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000221","code":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phoneMasked").value("139****0221"));

        assertThat(jdbc.queryForObject(
                "SELECT openid FROM app_user WHERE phone = '13900000221'", String.class)).isNotBlank();
    }

    @Test
    void bindPhoneForNewAccountStillRequiresNameAndPosition() throws Exception {
        MvcResult authorize = mockMvc.perform(get("/api/v1/c/auth/wechat/authorize")
                        .param("redirect", "/"))
                .andExpect(status().isOk())
                .andReturn();
        String authorizeUrl = JsonPath.read(authorize.getResponse().getContentAsString(), "$.data.authorizeUrl");
        String state = authorizeUrl.substring(authorizeUrl.indexOf("state=") + 6);
        Cookie authorizeCookie = cookie(authorize, "YHFC_C");
        MvcResult callback = mockMvc.perform(get("/api/v1/c/auth/wechat/callback")
                        .cookie(authorizeCookie)
                        .param("code", "bind-new")
                        .param("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needBindPhone").value(true))
                .andReturn();
        Cookie cookie = authorizeCookie;
        String csrf = JsonPath.read(callback.getResponse().getContentAsString(), "$.data.csrfToken");

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000251","purpose":"BIND"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/auth/wechat/bind-phone")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", csrf)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000251","code":"123456"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createInviteWithoutTypeIsRejected() throws Exception {
        Cookie admin = adminLogin("13900000231");

        mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "missing-type-invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiresInDays\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void dailyJobMarksExpiredInvitesAndAdminCanFilterThem() throws Exception {
        Cookie admin = adminLogin("13900000241");
        String token = createInvite(admin, "{\"type\":0}");
        Long inviteId = jdbc.queryForObject(
                "SELECT id FROM invite_link WHERE token_hash = SHA2(?, 256)", Long.class, token);
        jdbc.update("UPDATE invite_link SET expires_at = DATE_SUB(NOW(3), INTERVAL 1 DAY) WHERE id = ?",
                inviteId);

        inviteExpiryService.expire();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM invite_link WHERE id = ?", Integer.class, inviteId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/b/invites")
                        .cookie(admin)
                        .param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + inviteId + ")]").exists());
    }

    private Cookie register(String phone) throws Exception {
        return register(phone, null);
    }

    private Cookie register(String phone, String inviteToken) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        String invite = inviteToken == null ? "" : ",\"inviteToken\":\"" + inviteToken + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"邀请生命周期","position":2%s}
                                """.formatted(phone, invite)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Cookie adminLogin(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Admin123!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
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

    private String createInvite(Cookie admin, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/b/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "invite-lifecycle-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.data.inviteUrl");
        return url.substring(url.indexOf("token=") + 6);
    }

    private Integer usedCount(String token) {
        return jdbc.queryForObject(
                "SELECT used_count FROM invite_link WHERE token_hash = SHA2(?, 256)",
                Integer.class, token);
    }

    private Long insertCustomRole(String code) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)",
                code, code);
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertMatch(int totalMatchNo) {
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (1, '邀请生命周期测试', 0, '2026-12-02 10:00:00', '2026-12-02 12:00:00',
                    '2026-12-01 20:00:00', '2026-12-02 10:00:00', '测试球场', '2026',
                    '云河FC', '对手', 16, ?, ?, 1)
                """, totalMatchNo, totalMatchNo);
        return jdbc.queryForObject(
                "SELECT id FROM team_match WHERE team_id = 1 AND total_match_no = ?",
                Long.class, totalMatchNo);
    }
}
