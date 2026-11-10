package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthHardeningIT extends IntegrationTestSupport {

    @Test
    void guestCannotLoginToBackendOrObtainBackendSession() throws Exception {
        Long userId = insertActiveUser("13900000101", "Passw0rd!");
        assignRole(userId, "GUEST");

        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000101","password":"Passw0rd!"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301))
                .andReturn();

        assertThat(result.getResponse().getCookie("YHFC_B")).isNull();
    }

    @Test
    void customRoleWithBackendPermissionCanLogin() throws Exception {
        Long userId = insertActiveUser("13900000107", "Passw0rd!");
        Long roleId = insertCustomRole("APPROVER", "user:approve:any");
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);

        MvcResult login = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000107","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[?(@ == 'user:approve:any')]").exists())
                .andExpect(jsonPath("$.data.player").doesNotExist())
                .andReturn();

        mockMvc.perform(get("/api/v1/b/me").cookie(cookie(login, "YHFC_B")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[?(@ == 'dashboard')]").exists());
    }

    @Test
    void passwordResetAllowsLoginWithNewPassword() throws Exception {
        String phone = "13900000993";
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("OldPassw0rd!"));

        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"RESET"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/c/auth/password/reset")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","newPassword":"NewPassw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"NewPassw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanChangePhoneAndUserLogsInWithIt() throws Exception {
        Cookie admin = adminLogin("13900000994");
        Long userId = insertActiveUser("13900000995", "Passw0rd!");

        mockMvc.perform(patch("/api/v1/b/users/" + userId + "/phone")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000996"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phoneMasked").value("139****0996"));

        mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000996","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void unknownPhoneLocksOutAfterRepeatedFailures() throws Exception {
        String phone = "13900000991";
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

        mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Wrong123!"}
                                """.formatted(phone)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));

        sendSms(phone);
        mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"锁定后注册","position":2}
                                """.formatted(phone)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }

    @Test
    void pendingAccountMayUseWhitelistedWritesAndIsRejectedForOthers() throws Exception {
        Cookie pending = register("13900000102");
        MvcResult relogin = mockMvc.perform(post("/api/v1/c/auth/login")
                        .cookie(pending)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000102","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Cookie session = cookie(relogin, "YHFC_C");

        mockMvc.perform(post("/api/v1/c/matches/1/invites")
                        .cookie(session)
                        .with(csrf())
                        .header("Idempotency-Key", "pending-invite-blocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40303));

        mockMvc.perform(post("/api/v1/c/auth/logout")
                        .cookie(session)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void matchInviteRequiresConfirmedMatchPlayerOrAdmin() throws Exception {
        adminLogin("13900000111");
        Cookie admin = cLogin("13900000111", "Admin123!");
        Long matchId = insertMatch(1L, 101);
        Cookie formal = formalPlayerLogin("13900000112", 1L, 2);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/invites")
                        .cookie(formal)
                        .with(csrf())
                        .header("Idempotency-Key", "match-invite-unregistered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Long playerId = jdbc.queryForObject(
                "SELECT id FROM player WHERE user_id = (SELECT id FROM app_user WHERE phone = '13900000112')",
                Long.class);
        jdbc.update("""
                INSERT INTO registration (match_id, player_id, type, status)
                VALUES (?, ?, 1, 0)
                """, matchId, playerId);

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/invites")
                        .cookie(formal)
                        .with(csrf())
                        .header("Idempotency-Key", "match-invite-registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").isNotEmpty());

        mockMvc.perform(post("/api/v1/c/matches/" + matchId + "/invites")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "match-invite-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteUrl").isNotEmpty());
    }

    @Test
    void userObjectsExposeMaskedPhoneAndProfileFields() throws Exception {
        sendSms("13900000104");
        MvcResult registered = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000104","code":"123456","password":"Passw0rd!","name":"脱敏测试","position":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phoneMasked").value("139****0104"))
                .andExpect(jsonPath("$.data.user.name").value("脱敏测试"))
                .andExpect(jsonPath("$.data.user.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.phone").doesNotExist())
                .andReturn();
        Cookie cookie = cookie(registered, "YHFC_C");

        mockMvc.perform(get("/api/v1/c/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phoneMasked").value("139****0104"))
                .andExpect(jsonPath("$.data.user.name").value("脱敏测试"))
                .andExpect(jsonPath("$.data.user.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.phone").doesNotExist());

        insertActiveUser("13900000105", "Admin123!");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000105'", Long.class);
        assignRole(adminId, "ADMIN");
        MvcResult adminLogin = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000105","password":"Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(get("/api/v1/b/me").cookie(cookie(adminLogin, "YHFC_B")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phoneMasked").value("139****0105"))
                .andExpect(jsonPath("$.data.user.name").value("139****0105"))
                .andExpect(jsonPath("$.data.user.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.phone").doesNotExist());
    }

    @Test
    void rateLimitedResponseCarriesMatchingRetryAfterHeader() throws Exception {
        for (int i = 0; i < 5; i++) {
            sendSms("13900000106");
        }
        MvcResult limited = mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"13900000106","purpose":"REGISTER"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(42901))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber())
                .andExpect(header().exists("Retry-After"))
                .andReturn();
        int retryAfterSeconds = JsonPath.read(
                limited.getResponse().getContentAsString(), "$.data.retryAfterSeconds");
        assertThat(limited.getResponse().getHeader("Retry-After"))
                .isEqualTo(String.valueOf(retryAfterSeconds));
    }

    private Cookie register(String phone) throws Exception {
        sendSms(phone);
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"待审核","position":2}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private void sendSms(String phone) throws Exception {
        mockMvc.perform(post("/api/v1/c/auth/sms-codes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","purpose":"REGISTER"}
                                """.formatted(phone)))
                .andExpect(status().isOk());
    }

    private Cookie adminLogin(String phone) throws Exception {
        Long userId = insertActiveUser(phone, "Admin123!");
        assignRole(userId, "ADMIN");
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

    private Cookie cLogin(String phone, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Cookie formalPlayerLogin(String phone, Long teamId, int position) throws Exception {
        Long userId = insertActiveUser(phone, "Passw0rd!");
        assignRole(userId, "PLAYER");
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, skill)
                VALUES (?, ?, ?, 1, ?, 3)
                """, teamId, userId, "正式球员", position);
        MvcResult result = mockMvc.perform(post("/api/v1/c/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"Passw0rd!"}
                                """.formatted(phone)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_C");
    }

    private Long insertActiveUser(String phone, String password) {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode(password));
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private void assignRole(Long userId, String roleCode) {
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = ? AND team_id = 1", Long.class, roleCode);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private Long insertCustomRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)",
                code, code);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
        Long permissionId = jdbc.queryForObject(
                "SELECT id FROM permission WHERE code = ?", Long.class, permissionCode);
        jdbc.update("INSERT INTO role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
        return roleId;
    }

    private Long insertMatch(Long teamId, int totalMatchNo) {
        jdbc.update("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, capacity,
                    season_match_no, total_match_no, created_by)
                VALUES (?, '比赛邀请测试', 0, '2026-12-01 10:00:00', '2026-12-01 12:00:00',
                    '2026-11-30 20:00:00', '2026-12-01 10:00:00', '测试球场', '2026',
                    '云河FC', '对手', 16, ?, ?, 1)
                """, teamId, totalMatchNo, totalMatchNo);
        return jdbc.queryForObject(
                "SELECT id FROM team_match WHERE team_id = ? AND total_match_no = ?",
                Long.class, teamId, totalMatchNo);
    }
}
