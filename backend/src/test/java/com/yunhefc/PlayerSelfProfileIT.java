package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

class PlayerSelfProfileIT extends IntegrationTestSupport {

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void pendingUserUpdatesOwnProfileFields() throws Exception {
        String phone = "13900000501";
        Cookie session = registerPending(phone, "自助甲", 2);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long playerId = jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"自助乙","region":"宁波","position":3,
                                 "secondaryPositions":[0,1],"avatarUrl":"https://oss.example/self.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(playerId))
                .andExpect(jsonPath("$.data.name").value("自助乙"))
                .andExpect(jsonPath("$.data.jersey").value(nullValue()))
                .andExpect(jsonPath("$.data.type").value(0))
                .andExpect(jsonPath("$.data.typeLabel").value("GUEST"))
                .andExpect(jsonPath("$.data.position").value(3))
                .andExpect(jsonPath("$.data.positionLabel").value("FORWARD"))
                .andExpect(jsonPath("$.data.secondaryPositions[0]").value(0))
                .andExpect(jsonPath("$.data.secondaryPositionLabels[1]").value("DEFENDER"))
                .andExpect(jsonPath("$.data.region").value("宁波"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://oss.example/self.png"))
                .andExpect(jsonPath("$.data.skill").value(3));

        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("自助乙");
        assertThat(jdbc.queryForObject("SELECT region FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("宁波");
        assertThat(jdbc.queryForObject("SELECT position FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT secondary_positions FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("[0, 1]");
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("https://oss.example/self.png");
        assertThat(jdbc.queryForObject("SELECT skill FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'PLAYER_UPDATE'",
                Integer.class, playerId)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidPositionAndSecondaryPositions() throws Exception {
        String phone = "13900000502";
        Cookie session = registerPending(phone, "自助校验", 2);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long playerId = jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);

        expectValidationFailure(session, "{\"position\":4}");
        expectValidationFailure(session, "{\"secondaryPositions\":[4]}");
        expectValidationFailure(session, "{\"secondaryPositions\":[3,3]}");
        expectValidationFailure(session, "{\"secondaryPositions\":[0,1,2,3]}");
        expectValidationFailure(session, "{\"position\":3,\"secondaryPositions\":[2,3]}");
        expectValidationFailure(session, "{\"name\":\"   \"}");

        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("自助校验");
        assertThat(jdbc.queryForObject("SELECT position FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT secondary_positions FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("[]");
    }

    @Test
    void rejectsFieldsThatAreNotEditableBySelf() throws Exception {
        String phone = "13900000503";
        Cookie session = registerPending(phone, "自助越权", 2);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long playerId = jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jersey\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.jersey").value("不允许自助修改"));
        expectValidationFailure(session, "{\"skill\":5}");
        expectValidationFailure(session, "{\"type\":1}");
        expectValidationFailure(session, "{\"roles\":[\"ADMIN\"]}");
        expectValidationFailure(session, "{\"userId\":1}");
        expectValidationFailure(session, "{\"id\":1}");
        expectValidationFailure(session, "{\"status\":1}");

        assertThat(jdbc.queryForObject("SELECT jersey FROM player WHERE id = ?", Integer.class, playerId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT skill FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, playerId))
                .isZero();
    }

    @Test
    void derivedLabelFieldsAreIgnored() throws Exception {
        String phone = "13900000506";
        Cookie session = registerPending(phone, "自助标签", 2);

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionLabel":"FORWARD","secondaryPositionLabels":["GOALKEEPER"],
                                 "typeLabel":"PLAYER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(2))
                .andExpect(jsonPath("$.data.secondaryPositions.length()").value(0));
    }

    @Test
    void userWithoutPlayerProfileGetsNotFound() throws Exception {
        Long roleId = insertRole("M02_SELF_NO_PROFILE", "player:edit:own");
        insertActiveUser("13900000507", roleId);
        Cookie session = cLogin("13900000507", "Passw0rd!");

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无档案\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void partialUpdateClearsOptionalFieldsOnly() throws Exception {
        String phone = "13900000504";
        Cookie session = registerPending(phone, "自助部分", 2);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long playerId = jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
        jdbc.update("UPDATE player SET region = '杭州', avatar_url = 'https://oss.example/before.png' WHERE id = ?",
                playerId);

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"region\":\"\",\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("自助部分"))
                .andExpect(jsonPath("$.data.region").value(nullValue()))
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()));

        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("自助部分");
        assertThat(jdbc.queryForObject("SELECT secondary_positions FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo("[]");
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/c/me/player")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"未登录\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void userWithoutSelfEditPermissionIsForbidden() throws Exception {
        Long roleId = insertRole("M02_SELF_VIEWER", "player:view:any");
        Long userId = insertActiveUser("13900000505", roleId);
        insertFormalPlayer(userId, "自助无权限", 802);
        Cookie session = cLogin("13900000505", "Passw0rd!");

        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    private void expectValidationFailure(Cookie session, String body) throws Exception {
        mockMvc.perform(patch("/api/v1/c/me/player")
                        .cookie(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    private Cookie registerPending(String phone, String name, int position) throws Exception {
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
                                {"phone":"%s","code":"123456","password":"Passw0rd!","name":"%s","position":%d}
                                """.formatted(phone, name, position)))
                .andExpect(status().isOk())
                .andReturn();
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        createdUserIds.add(userId);
        return cookie(result, "YHFC_C");
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

    private Long insertActiveUser(String phone, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Passw0rd!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private void insertFormalPlayer(Long userId, String name, int jersey) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, 1, 1, '[]', 3)
                """, userId, name, jersey);
    }

    private Long insertRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)", code, code);
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
        jdbc.update("""
                INSERT INTO role_permission (role_id, permission_id)
                SELECT ?, id FROM permission WHERE code = ?
                """, roleId, permissionCode);
        return roleId;
    }
}
