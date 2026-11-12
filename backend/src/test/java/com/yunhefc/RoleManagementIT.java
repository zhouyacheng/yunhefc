package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RoleManagementIT extends IntegrationTestSupport {

    @Test
    void roleCrudPermissionsAndGuards() throws Exception {
        Cookie admin = adminLogin("13900000051");
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000051'", Long.class);

        MvcResult created = mockMvc.perform(post("/api/v1/b/roles")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"COACH","name":"教练","permissions":["lineup:view:any","grouping:view:any"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("COACH"))
                .andReturn();
        Number roleId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/b/roles")
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"COACH","name":"重复","permissions":[]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40912));

        mockMvc.perform(patch("/api/v1/b/roles/" + roleId)
                        .cookie(admin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"教练组\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("教练组"));

        mockMvc.perform(put("/api/v1/b/roles/" + roleId + "/permissions")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "role-perm-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"lineup:view:any\",\"grouping:view:any\",\"match:view:any\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(3));

        Long playerRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'PLAYER' AND team_id = 1", Long.class);
        Long guestRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'GUEST' AND team_id = 1", Long.class);

        mockMvc.perform(delete("/api/v1/b/roles/" + guestRoleId)
                        .cookie(admin)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40914));

        mockMvc.perform(put("/api/v1/b/users/" + adminId + "/roles")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "role-user-last-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[" + playerRoleId + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40913));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_role WHERE user_id = ? AND is_deleted = 0",
                Integer.class, adminId)).isGreaterThanOrEqualTo(1);

        Long adminRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
        mockMvc.perform(put("/api/v1/b/roles/" + adminRoleId + "/permissions")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "role-perm-last-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"role:view:any\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40913));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM role_permission rp JOIN permission p ON p.id = rp.permission_id "
                        + "WHERE rp.role_id = ? AND rp.is_deleted = 0 AND p.code = 'role:manage:any'",
                Integer.class, adminRoleId)).isEqualTo(1);
    }

    @Test
    void userListValidatesStatusAndSearchesNameOrPhone() throws Exception {
        Cookie admin = adminLogin("13900000071");
        Long playerRoleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'PLAYER' AND team_id = 1", Long.class);
        Long nameUserId = insertUserWithRole("13900000072", 1, playerRoleId);
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, skill)
                VALUES (1, ?, '关键字甲', 1, 1, 3)
                """, nameUserId);
        Long phoneUserId = insertUserWithRole("13900000083", 1, playerRoleId);

        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("keyword", "  关键字甲  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(nameUserId))
                .andExpect(jsonPath("$.data.items[0].wechatBound").doesNotExist());

        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("keyword", "0083"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(phoneUserId));

        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));

        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("keyword", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("keyword", "a".repeat(65)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(get("/api/v1/b/users").cookie(admin).param("status", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void rolePermissionChangeNotifiesOnlyActiveUsers() throws Exception {
        Cookie admin = adminLogin("13900000052");
        jdbc.update("INSERT INTO app_role (team_id, code, name, is_default) VALUES (1, 'NOTIFY_ROLE', '通知测试', 0)");
        Long roleId = jdbc.queryForObject(
                "SELECT id FROM app_role WHERE code = 'NOTIFY_ROLE' AND team_id = 1", Long.class);

        Long activeId = insertUserWithRole("13900000061", 1, roleId);
        Long pendingId = insertUserWithRole("13900000062", 0, roleId);
        Long disabledId = insertUserWithRole("13900000063", 2, roleId);

        mockMvc.perform(put("/api/v1/b/roles/" + roleId + "/permissions")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", "role-notify-active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"lineup:view:any\"]}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'ROLE_CHANGED' AND aggregate_id = ?",
                Integer.class, activeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'ROLE_CHANGED' AND aggregate_id IN (?, ?)",
                Integer.class, pendingId, disabledId)).isZero();

        // 清理额外管理员，避免影响其他用例的“最后管理员”断言
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM app_user WHERE phone = '13900000052'", Long.class);
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", adminId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", adminId);
    }

    private Long insertUserWithRole(String phone, int status, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, ?)", phone, status);
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private Cookie adminLogin(String phone) throws Exception {
        jdbc.update("INSERT INTO app_user (phone, password_hash, status) VALUES (?, ?, 1)",
                phone, passwordEncoder.encode("Admin123!"));
        Long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
        Long roleId = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'ADMIN' AND team_id = 1", Long.class);
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
}
