package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
