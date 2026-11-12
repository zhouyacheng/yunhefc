package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

class PlayerAdminIT extends IntegrationTestSupport {

    private static final String ULID = "01J9ZQ8K3M4N5P6Q7R8S9T0VWX";

    private final List<Long> createdUserIds = new ArrayList<>();

    @Autowired
    private PlayerProfileApplicationService playerProfileService;

    @AfterEach
    void cleanup() {
        for (Long userId : createdUserIds) {
            jdbc.update("DELETE FROM player WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    @Test
    void listFiltersSortsAndValidatesPaging() throws Exception {
        Cookie admin = adminLogin("13900000401");
        Long playerRole = roleId("PLAYER");
        Long guestRole = roleId("GUEST");
        Long adminRole = roleId("ADMIN");

        Long formalUserId = insertUser("13900000411", 1, playerRole);
        insertPlayer(formalUserId, "M02列表甲", 712, 1, 3, 5, "杭州");
        String formalAvatarKey = "avatar/" + formalUserId + "/" + ULID + ".jpg";
        jdbc.update("UPDATE player SET avatar_url = ? WHERE user_id = ?", formalAvatarKey, formalUserId);
        Long pendingUserId = insertUser("13900000412", 0, guestRole);
        insertPlayer(pendingUserId, "M02列表乙", 713, 0, 2, 3, "宁波");
        Long adminPlayerUserId = insertUser("13900000413", 1, adminRole);
        insertPlayer(adminPlayerUserId, "M02列表丙", 714, 1, 1, 4, "上海");

        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("name", "M02列表"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表甲"))
                .andExpect(jsonPath("$.data.items[0].typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.items[0].positionLabel").value("FORWARD"))
                .andExpect(jsonPath("$.data.items[0].statusLabel").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].admin").value(false))
                .andExpect(jsonPath("$.data.items[0].phoneMasked").value("139****0411"))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("PLAYER"))
                .andExpect(jsonPath("$.data.items[0].secondaryPositions[0]").value(1))
                .andExpect(jsonPath("$.data.items[0].secondaryPositionLabels[1]").value("MIDFIELDER"))
                .andExpect(jsonPath("$.data.items[0].avatarUrl").value("mock://oss/" + formalAvatarKey))
                .andExpect(jsonPath("$.data.items[1].avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("type", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表乙"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表乙"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("position", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表丙"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("skill", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表甲"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("region", "宁"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表乙"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("admin", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表丙"))
                .andExpect(jsonPath("$.data.items[0].admin").value(true));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("admin", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("sort", "jersey:desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].jersey").value(714));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("sort", "skill:desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("M02列表甲"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageSize").value(20));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("pageSize", "15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02列表").param("sort", "name:asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("type", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("status", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("position", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(get("/api/v1/b/players").cookie(admin).param("skill", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void adminFilterAlsoMatchesCustomRoleWithRoleManagePermission() throws Exception {
        Cookie admin = adminLogin("13900000403");
        Long roleManagerRole = insertRole("M02_ROLE_MANAGER", "role:manage:any");
        Long userId = insertUser("13900000416", 1, roleManagerRole);
        insertPlayer(userId, "M02权限管理员", 715, 1, 0, 3, null);

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02权限管理员").param("admin", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("M02权限管理员"))
                .andExpect(jsonPath("$.data.items[0].admin").value(true))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("M02_ROLE_MANAGER"));

        mockMvc.perform(get("/api/v1/b/players").cookie(admin)
                        .param("name", "M02权限管理员").param("admin", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void updateOnlyTargetColumnsAndRejectsDuplicateFormalJersey() throws Exception {
        Cookie admin = adminLogin("13900000402");
        Long playerRole = roleId("PLAYER");
        Long guestRole = roleId("GUEST");

        Long userIdA = insertUser("13900000421", 1, playerRole);
        Long playerA = insertPlayer(userIdA, "M02编辑甲", 720, 1, 1, 3, "上海");
        Long userIdB = insertUser("13900000422", 1, playerRole);
        Long playerB = insertPlayer(userIdB, "M02编辑乙", 721, 1, 2, 3, null);
        Long guestUserId = insertUser("13900000423", 0, guestRole);
        Long guestPlayer = insertPlayer(guestUserId, "M02编辑丙", 720, 0, 1, 3, null);
        String avatarKey = "avatar/" + userIdA + "/" + ULID + ".jpg";

        mockMvc.perform(patch("/api/v1/b/players/" + playerA)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"M02编辑甲改","jersey":722,"region":"宁波","position":3,
                                 "secondaryPositions":[1,2],"skill":5,"avatarUrl":"%s",
                                 "type":0,"userId":999999,"status":2}
                                """.formatted(avatarKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("M02编辑甲改"))
                .andExpect(jsonPath("$.data.avatarUrl").value("mock://oss/" + avatarKey))
                .andExpect(jsonPath("$.data.jersey").value(722))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.typeLabel").value("PLAYER"))
                .andExpect(jsonPath("$.data.position").value(3))
                .andExpect(jsonPath("$.data.positionLabel").value("FORWARD"))
                .andExpect(jsonPath("$.data.secondaryPositions[0]").value(1))
                .andExpect(jsonPath("$.data.secondaryPositionLabels[1]").value("MIDFIELDER"))
                .andExpect(jsonPath("$.data.skill").value(5))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusLabel").value("ACTIVE"))
                .andExpect(jsonPath("$.data.admin").value(false))
                .andExpect(jsonPath("$.data.roles[0]").value("PLAYER"))
                .andExpect(jsonPath("$.data.userId").value(userIdA));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE id = ?", Integer.class, playerA))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM player WHERE id = ?", Long.class, playerA))
                .isEqualTo(userIdA);
        assertThat(jdbc.queryForObject("SELECT status FROM app_user WHERE id = ?", Integer.class, userIdA))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerA))
                .isEqualTo(avatarKey);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'PLAYER_UPDATE'",
                Integer.class, playerA)).isEqualTo(1);
        String afterJson = jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.afterJson')) FROM outbox_event "
                        + "WHERE event_type = 'AUDIT_RECORDED' AND aggregate_id = ? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.action')) = 'PLAYER_UPDATE'",
                String.class, playerA);
        assertThat(afterJson).contains(avatarKey).doesNotContain("mock://");

        mockMvc.perform(patch("/api/v1/b/players/" + playerB)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jersey\":722}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jersey\":722}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jersey").value(722));

        mockMvc.perform(patch("/api/v1/b/players/" + playerA)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":1,\"region\":\"\",\"avatarUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.secondaryPositions[0]").value(2))
                .andExpect(jsonPath("$.data.secondaryPositions.length()").value(1))
                .andExpect(jsonPath("$.data.region").value(nullValue()))
                .andExpect(jsonPath("$.data.avatarUrl").value(nullValue()));

        assertThat(jdbc.queryForObject("SELECT region FROM player WHERE id = ?", String.class, playerA))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT avatar_url FROM player WHERE id = ?", String.class, playerA))
                .isNull();

        mockMvc.perform(patch("/api/v1/b/players/" + playerA)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不应写入\",\"avatarUrl\":\"https://oss.example/a.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data.details.avatarUrl").value("头像对象键格式不正确"));
        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, playerA))
                .isEqualTo("M02编辑甲改");

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":3,\"secondaryPositions\":[3]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secondaryPositions\":[1,1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"secondaryPositions\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skill\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(patch("/api/v1/b/players/" + guestPlayer)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jersey\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void playerEndpointsRequirePermissions() throws Exception {
        Long viewerRole = insertRole("M02_VIEWER", "match:view:any");
        Long viewerId = insertUser("13900000431", 1, viewerRole);
        Cookie viewer = login("13900000431", "Viewer123!");

        Long playerRole = roleId("PLAYER");
        Long userId = insertUser("13900000432", 1, playerRole);
        Long playerId = insertPlayer(userId, "M02权限", 730, 1, 0, 3, null);

        mockMvc.perform(get("/api/v1/b/players").cookie(viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(patch("/api/v1/b/players/" + playerId)
                        .cookie(viewer)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/v1/b/players"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        assertThat(viewerId).isPositive();
    }

    @Test
    void viewAndEditPermissionsAreEnforcedSeparately() throws Exception {
        Long viewRole = insertRole("M02_ONLY_VIEW", "player:view:any");
        insertUser("13900000441", 1, viewRole);
        Cookie viewer = login("13900000441", "Viewer123!");

        Long editRole = insertRole("M02_ONLY_EDIT", "player:edit:any");
        insertUser("13900000442", 1, editRole);
        Cookie editor = login("13900000442", "Editor123!");

        Long targetUserId = insertUser("13900000443", 1, roleId("PLAYER"));
        Long targetPlayerId = insertPlayer(targetUserId, "M02权限分离", 750, 1, 0, 3, null);

        mockMvc.perform(get("/api/v1/b/players").cookie(viewer)
                        .param("name", "M02权限分离"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(patch("/api/v1/b/players/" + targetPlayerId)
                        .cookie(viewer)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"仅查看不能改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/v1/b/players").cookie(editor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(patch("/api/v1/b/players/" + targetPlayerId)
                        .cookie(editor)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"仅编辑可以改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("仅编辑可以改"));

        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, targetPlayerId))
                .isEqualTo("仅编辑可以改");
    }

    @Test
    void promotingGuestWithDuplicateFormalJerseyReturnsConflict() {
        Long formalUserId = insertUser("13900000451", 1, roleId("PLAYER"));
        insertPlayer(formalUserId, "M02转正甲", 760, 1, 1, 3, null);
        Long guestUserId = insertUser("13900000452", 1, roleId("GUEST"));
        insertPlayer(guestUserId, "M02转正乙", 760, 0, 2, 3, null);

        assertThatThrownBy(() -> playerProfileService.promoteToFormal(guestUserId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DUPLICATE_JERSEY));

        assertThat(jdbc.queryForObject("SELECT type FROM player WHERE user_id = ?", Integer.class, guestUserId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE user_id = ?", String.class, guestUserId))
                .isEqualTo("M02转正乙");
    }

    private Long insertUser(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        createdUserIds.add(userId);
        return userId;
    }

    private Long insertPlayer(Long userId, String name, Integer jersey, int type, int position,
                              int skill, String region) {
        String secondary = switch (position) {
            case 0 -> "[1,2]";
            case 1 -> "[2,3]";
            case 2 -> "[1,3]";
            default -> "[1,2]";
        };
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position,
                    secondary_positions, region, skill)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, name, jersey, type, position, secondary, region, skill);
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
        insertUser(phone, 1, roleId("ADMIN"));
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
