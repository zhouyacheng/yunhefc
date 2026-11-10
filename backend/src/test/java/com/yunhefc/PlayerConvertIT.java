package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.contracts.player.PlayerType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PlayerConvertIT extends IntegrationTestSupport {

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
    void convertingGuestTurnsFormalAssignsJerseyAndPlayerRole() throws Exception {
        Cookie admin = adminLogin("13900000901");
        Long userId = insertUser("13900000911", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02转正甲", 7, PlayerType.GUEST.code(), 1);

        convert(admin, playerId, "{\"jersey\":9}", "convert-911")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(playerId))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.jersey").value(9))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusLabel").value("ACTIVE"))
                .andExpect(jsonPath("$.data.roles[0]").value("PLAYER"));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(PlayerType.PLAYER.code());
        assertThat(jdbc.queryForObject("SELECT jersey FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(9);
        assertThat(roleCodes(userId)).containsExactly("PLAYER");

        Map<String, Object> audit = latestAudit(playerId);
        assertThat(audit.get("action")).isEqualTo("PLAYER_CONVERT");
        assertThat(audit.get("target_type")).isEqualTo("PLAYER");
        assertThat((String) audit.get("before_json"))
                .contains("\"type\":0", "\"jersey\":7", "\"GUEST\"");
        assertThat((String) audit.get("after_json"))
                .contains("\"type\":1", "\"jersey\":9", "\"PLAYER\"");
    }

    @Test
    void convertWithoutJerseyKeepsExistingNumber() throws Exception {
        Cookie admin = adminLogin("13900000902");
        Long userId = insertUser("13900000912", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02转正乙", 12, PlayerType.GUEST.code(), 2);

        convert(admin, playerId, "{}", "convert-912")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jersey").value(12))
                .andExpect(jsonPath("$.data.type").value(1));

        assertThat(jdbc.queryForObject("SELECT jersey FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(12);
    }

    @Test
    void convertRejectsJerseyTakenByFormalPlayerAndRollsBack() throws Exception {
        Cookie admin = adminLogin("13900000903");
        Long formalUserId = insertUser("13900000913", UserStatus.ACTIVE.code(), roleId("PLAYER"));
        insertPlayer(formalUserId, "M02转正丙", 30, PlayerType.PLAYER.code(), 0);
        Long userId = insertUser("13900000914", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02转正丁", 5, PlayerType.GUEST.code(), 2);

        convert(admin, playerId, "{\"jersey\":30}", "convert-914")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, playerId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT jersey FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(5);
        assertThat(roleCodes(userId)).containsExactly("GUEST");
        assertThat(auditCount(playerId)).isZero();

        Long collisionUserId = insertUser("13900000915", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long collisionPlayerId = insertPlayer(collisionUserId, "M02转正戊", 30, PlayerType.GUEST.code(), 3);

        convert(admin, collisionPlayerId, "{}", "convert-915")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, collisionPlayerId))
                .isZero();
        assertThat(auditCount(collisionPlayerId)).isZero();
    }

    @Test
    void convertAllowsJerseyHeldByAnotherGuest() throws Exception {
        Cookie admin = adminLogin("13900000909");
        Long otherUserId = insertUser("13900000923", UserStatus.ACTIVE.code(), roleId("GUEST"));
        insertPlayer(otherUserId, "M02转正丑", 40, PlayerType.GUEST.code(), 2);
        Long userId = insertUser("13900000924", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02转正寅", 41, PlayerType.GUEST.code(), 1);

        convert(admin, playerId, "{\"jersey\":40}", "convert-923")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jersey").value(40))
                .andExpect(jsonPath("$.data.type").value(1));

        assertThat(jdbc.queryForObject("SELECT jersey FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(40);
        assertThat(roleCodes(userId)).containsExactly("PLAYER");
    }

    @Test
    void convertRequiresActiveGuestAccount() throws Exception {
        Cookie admin = adminLogin("13900000904");
        Long pendingUserId = insertUser("13900000916", UserStatus.PENDING.code(), roleId("GUEST"));
        Long pendingPlayerId = insertPlayer(pendingUserId, "M02转正己", 15, PlayerType.GUEST.code(), 1);
        Long disabledUserId = insertUser("13900000917", UserStatus.DISABLED.code(), roleId("GUEST"));
        Long disabledPlayerId = insertPlayer(disabledUserId, "M02转正庚", 16, PlayerType.GUEST.code(), 1);

        convert(admin, pendingPlayerId, "{}", "convert-916")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        convert(admin, disabledPlayerId, "{}", "convert-917")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, pendingPlayerId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, disabledPlayerId))
                .isZero();
        assertThat(roleCodes(pendingUserId)).containsExactly("GUEST");
        assertThat(roleCodes(disabledUserId)).containsExactly("GUEST");
        assertThat(auditCount(pendingPlayerId)).isZero();
        assertThat(auditCount(disabledPlayerId)).isZero();
    }

    @Test
    void convertingFormalPlayerIsIdempotentWithoutSideEffects() throws Exception {
        Cookie admin = adminLogin("13900000905");
        Long customRole = insertRole("M02_CONVERT_FORMAL_CUSTOM", "player:view:any");
        Long userId = insertUser("13900000918", UserStatus.ACTIVE.code(),
                roleId("PLAYER"), customRole);
        Long playerId = insertPlayer(userId, "M02转正辛", 21, PlayerType.PLAYER.code(), 1);

        convert(admin, playerId, "{\"jersey\":99}", "convert-918")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(playerId))
                .andExpect(jsonPath("$.data.jersey").value(21))
                .andExpect(jsonPath("$.data.type").value(1));

        assertThat(jdbc.queryForObject("SELECT jersey FROM player WHERE id = ?", Integer.class, playerId))
                .isEqualTo(21);
        assertThat(roleCodes(userId)).containsExactlyInAnyOrder("PLAYER", "M02_CONVERT_FORMAL_CUSTOM");
        assertThat(auditCount(playerId)).isZero();
    }

    @Test
    void convertReplacesOnlyGuestRoleKeepingCustomRoles() throws Exception {
        Cookie admin = adminLogin("13900000906");
        Long customRole = insertRole("M02_CONVERT_KEEP_CUSTOM", "player:view:any");
        Long userId = insertUser("13900000919", UserStatus.ACTIVE.code(),
                roleId("GUEST"), customRole);
        Long playerId = insertPlayer(userId, "M02转正壬", 17, PlayerType.GUEST.code(), 2);

        convert(admin, playerId, "{}", "convert-919")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value(1));

        assertThat(roleCodes(userId)).containsExactlyInAnyOrder("PLAYER", "M02_CONVERT_KEEP_CUSTOM");
    }

    @Test
    void convertRejectsUnknownAndOtherTeamPlayers() throws Exception {
        Cookie admin = adminLogin("13900000908");
        Long userId = insertUser("13900000922", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long otherTeamPlayerId = insertPlayer(userId, "M02转正子", 19, PlayerType.GUEST.code(), 1);
        jdbc.update("UPDATE player SET team_id = 2 WHERE id = ?", otherTeamPlayerId);

        convert(admin, 999_999_999L, "{}", "convert-921")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        convert(admin, otherTeamPlayerId, "{}", "convert-922")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, otherTeamPlayerId))
                .isZero();
        assertThat(roleCodes(userId)).containsExactly("GUEST");
    }

    @Test
    void convertRequiresPermissionAndIdempotencyKey() throws Exception {
        Long viewerRole = insertRole("M02_CONVERT_VIEWER", "match:view:any");
        insertUser("13900000920", UserStatus.ACTIVE.code(), viewerRole);
        Cookie viewer = login("13900000920", "Viewer123!");
        Cookie admin = adminLogin("13900000907");
        Long userId = insertUser("13900000921", UserStatus.ACTIVE.code(), roleId("GUEST"));
        Long playerId = insertPlayer(userId, "M02转正癸", 18, PlayerType.GUEST.code(), 0);

        convert(viewer, playerId, "{}", "convert-920")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/convert")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/v1/b/players/" + playerId + "/convert")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, playerId))
                .isZero();
        assertThat(roleCodes(userId)).containsExactly("GUEST");
    }

    private ResultActions convert(
            Cookie actor, Long playerId, String body, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/b/players/" + playerId + "/convert")
                .cookie(actor)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Idempotency-Key", idempotencyKey));
    }

    private List<String> roleCodes(Long userId) {
        return jdbc.queryForList("""
                SELECT r.code FROM user_role ur
                JOIN app_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND ur.is_deleted = 0 AND r.is_deleted = 0
                """, String.class, userId);
    }

    private Map<String, Object> latestAudit(Long playerId) {
        return jdbc.queryForMap("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) AS action,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.targetType')) AS target_type,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.beforeJson')) AS before_json,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) AS after_json
                FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                ORDER BY id DESC LIMIT 1
                """, playerId);
    }

    private int auditCount(Long playerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_event
                WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ?
                  AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'PLAYER_CONVERT'
                """, Integer.class, playerId);
    }

    private Long insertUser(String phone, int status, Long... roleIds) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        for (Long roleId : roleIds) {
            jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        createdUserIds.add(userId);
        return userId;
    }

    private Long insertPlayer(Long userId, String name, Integer jersey, int type, int position) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position, secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, ?, '[]', 3)
                """, userId, name, jersey, type, position);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertRole(String code, String permissionCode) {
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, ?, ?, 0)", code, code);
        Long roleId = roleId(code);
        jdbc.update("""
                INSERT INTO role_permission (role_id, permission_id)
                SELECT ?, id FROM permission WHERE code = ?
                """, roleId, permissionCode);
        return roleId;
    }

    private Cookie adminLogin(String phone) throws Exception {
        insertUser(phone, UserStatus.ACTIVE.code(), roleId("ADMIN"));
        return login(phone, "Admin123!");
    }

    private Cookie login(String phone, String password) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, password)))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }
}
