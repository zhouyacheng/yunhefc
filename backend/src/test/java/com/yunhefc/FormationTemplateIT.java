package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.lineup.domain.FormationTemplate;
import com.yunhefc.lineup.infrastructure.FormationTemplateMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * M07 阵型模板（LIN-05~08）：权限、预置只读、复制、重命名、软删除与历史阵容引用。
 */
class FormationTemplateIT extends IntegrationTestSupport {

    @Autowired
    private FormationTemplateMapper templateMapper;

    @Test
    void templateEndpointsRequireLineupEditPermission() throws Exception {
        mockMvc.perform(get("/api/v1/b/formation-templates"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        insertUser("13900003101", roleId("PLAYER"));
        Cookie readOnly = backendLogin("13900003101");
        list(readOnly).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        create(readOnly, presetId(), "无权模板").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        rename(readOnly, presetId(), "无权改名").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
        mockMvc.perform(delete("/api/v1/b/formation-templates/" + presetId())
                        .cookie(readOnly).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));

        Cookie admin = adminLogin("13900003102", "模板管理员");
        list(admin).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void listsEightPresetsWithSlotsAndFiltersCustomTemplates() throws Exception {
        Cookie admin = adminLogin("13900003201", "模板列表");

        list(admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(8)))
                .andExpect(jsonPath("$.data.items[0].isPreset").value(true))
                .andExpect(jsonPath("$.data.items[0].name").value("2-3-2"))
                .andExpect(jsonPath("$.data.items[0].slots", hasSize(8)))
                .andExpect(jsonPath("$.data.items[0].slots[0].slotKey").value("gk"))
                .andExpect(jsonPath("$.data.items[0].slots[0].role").value(0))
                .andExpect(jsonPath("$.data.items[0].slots[0].roleLabel").value("GOALKEEPER"))
                .andExpect(jsonPath("$.data.items[0].slots[0].x").value(50.0))
                .andExpect(jsonPath("$.data.items[0].slots[0].y").value(88.0));

        list(admin, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        Long baseId = presetId();
        MvcResult created = create(admin, baseId, "我方 3-2-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.template.name").value("我方 3-2-2"))
                .andExpect(jsonPath("$.data.template.isPreset").value(false))
                .andExpect(jsonPath("$.data.template.baseTemplateId").value(baseId))
                .andExpect(jsonPath("$.data.template.slots", hasSize(8)))
                .andReturn();
        Long customId = JsonPathHelper.dataLong(created, "$.data.template.id");

        list(admin)
                .andExpect(jsonPath("$.data.items", hasSize(9)))
                .andExpect(jsonPath("$.data.items[8].id").value(customId))
                .andExpect(jsonPath("$.data.items[8].isPreset").value(false));
        list(admin, false)
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(customId));

        assertThat(jdbc.queryForObject(
                "SELECT team_id FROM formation_template WHERE id = ?", Long.class, customId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT is_preset FROM formation_template WHERE id = ?", Integer.class, customId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formation_slot WHERE template_id = ?", Integer.class, customId))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM formation_slot c
                JOIN formation_slot b ON b.template_id = ? AND b.slot_key = c.slot_key
                    AND b.role = c.role AND b.x = c.x AND b.y = c.y
                WHERE c.template_id = ?
                """, Integer.class, baseId, customId)).isEqualTo(8);
    }

    @Test
    void createValidatesNameAndBaseTemplate() throws Exception {
        Cookie admin = adminLogin("13900003301", "模板复制");

        create(admin, presetId(), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        mockMvc.perform(post("/api/v1/b/formation-templates")
                        .cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"缺少来源"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
        create(admin, 999_999L, "来源不存在")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        MvcResult created = create(admin, presetId(), "自定义来源")
                .andExpect(status().isOk()).andReturn();
        Long customId = JsonPathHelper.dataLong(created, "$.data.template.id");
        create(admin, customId, "复制自定义")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        mockMvc.perform(delete("/api/v1/b/formation-templates/" + customId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk());
        create(admin, customId, "复制已删除")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
    }

    @Test
    void renamesAndDeletesCustomTemplatesOnly() throws Exception {
        Cookie admin = adminLogin("13900003401", "模板维护");
        Long presetId = presetId();
        MvcResult created = create(admin, presetId, "可维护模板")
                .andExpect(status().isOk()).andReturn();
        Long customId = JsonPathHelper.dataLong(created, "$.data.template.id");

        rename(admin, customId, "  重命名模板  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.template.name").value("重命名模板"))
                .andExpect(jsonPath("$.data.template.slots", hasSize(8)));
        assertThat(jdbc.queryForObject(
                "SELECT name FROM formation_template WHERE id = ?", String.class, customId))
                .isEqualTo("重命名模板");

        rename(admin, presetId, "修改预置")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));
        rename(admin, 999_999L, "不存在")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        rename(admin, customId, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        mockMvc.perform(delete("/api/v1/b/formation-templates/" + presetId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42201));

        mockMvc.perform(delete("/api/v1/b/formation-templates/" + customId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));
        assertThat(jdbc.queryForObject(
                "SELECT is_deleted FROM formation_template WHERE id = ?", Integer.class, customId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formation_slot WHERE template_id = ?", Integer.class, customId))
                .isEqualTo(8);
        list(admin, false).andExpect(jsonPath("$.data.items", hasSize(0)));

        mockMvc.perform(delete("/api/v1/b/formation-templates/" + customId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
        rename(admin, customId, "已删除")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void softDeletedTemplateKeepsHistoricalLineupReference() throws Exception {
        Cookie admin = adminLogin("13900003501", "模板历史");
        MvcResult created = create(admin, presetId(), "历史模板")
                .andExpect(status().isOk()).andReturn();
        Long customId = JsonPathHelper.dataLong(created, "$.data.template.id");

        jdbc.update("""
                INSERT INTO lineup (match_id, side, formation_template_id, version)
                VALUES (?, 0, ?, 3)
                """, 900_001L, customId);
        Long lineupId = jdbc.queryForObject(
                "SELECT id FROM lineup WHERE match_id = ? AND side = 0", Long.class, 900_001L);

        mockMvc.perform(delete("/api/v1/b/formation-templates/" + customId)
                        .cookie(admin).with(csrf()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT formation_template_id FROM lineup WHERE id = ?", Long.class, lineupId))
                .isEqualTo(customId);
        assertThat(jdbc.queryForObject(
                "SELECT is_deleted FROM formation_template WHERE id = ?", Integer.class, customId))
                .isEqualTo(1);

        // 列表与常规读取不展示已删除模板；历史阵容读取必须显式包含。
        assertThat(templateMapper.selectById(customId)).isNull();
        FormationTemplate historical = templateMapper.selectByIdIncludingDeleted(customId);
        assertThat(historical).isNotNull();
        assertThat(historical.getName()).isEqualTo("历史模板");
        assertThat(historical.getIsDeleted()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM formation_slot WHERE template_id = ?", Integer.class, customId))
                .isEqualTo(8);
    }

    private ResultActions list(Cookie cookie) throws Exception {
        return list(cookie, true);
    }

    private ResultActions list(Cookie cookie, boolean includePreset) throws Exception {
        return mockMvc.perform(get("/api/v1/b/formation-templates")
                .queryParam("includePreset", Boolean.toString(includePreset))
                .cookie(cookie));
    }

    private ResultActions create(Cookie cookie, Long baseTemplateId, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/b/formation-templates")
                .cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"baseTemplateId":%d,"name":"%s"}
                        """.formatted(baseTemplateId, name)));
    }

    private ResultActions rename(Cookie cookie, Long id, String name) throws Exception {
        return mockMvc.perform(patch("/api/v1/b/formation-templates/" + id)
                .cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s"}
                        """.formatted(name)));
    }

    private Long presetId() {
        return jdbc.queryForObject(
                "SELECT id FROM formation_template WHERE is_preset = 1 ORDER BY id LIMIT 1",
                Long.class);
    }

    private Cookie adminLogin(String phone, String name) throws Exception {
        insertUser(phone, roleId("ADMIN"));
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, type, position, jersey,
                    secondary_positions, skill)
                VALUES (1, ?, ?, 1, 0, ?, '[]', 3)
                """, userId(phone), name, 300 + Integer.parseInt(phone.substring(phone.length() - 2)));
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Admin123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, "Admin123!")))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    private Long insertUser(String phone, Long roleId) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = userId(phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    private Cookie backendLogin(String phone) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Player123!"), phone);
        MvcResult result = mockMvc.perform(post("/api/v1/b/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","password":"%s"}
                                """.formatted(phone, "Player123!")))
                .andExpect(status().isOk())
                .andReturn();
        return cookie(result, "YHFC_B");
    }

    private static final class JsonPathHelper {

        private JsonPathHelper() {
        }

        private static Long dataLong(MvcResult result, String path) throws Exception {
            Number value = com.jayway.jsonpath.JsonPath.read(
                    result.getResponse().getContentAsString(), path);
            return value.longValue();
        }
    }
}
