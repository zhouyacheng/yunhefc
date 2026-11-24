package com.yunhefc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhefc.common.time.ShanghaiTime;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

/**
 * 集成测试的账号、球员与登录夹具；每个测试方法由父类重置数据库，无需清理。
 */
abstract class AccountITSupport extends IntegrationTestSupport {

    protected Long insertUser(String phone, String roleCode) {
        jdbc.update("INSERT INTO app_user (phone, status) VALUES (?, 1)", phone);
        Long userId = userId(phone);
        jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)",
                userId, roleId(roleCode));
        return userId;
    }

    protected Long insertPlayer(Long userId, String name, int jersey, int type) {
        jdbc.update("""
                INSERT INTO player (team_id, user_id, name, jersey, type, position,
                                    secondary_positions, skill)
                VALUES (1, ?, ?, ?, ?, 1, '[]', 3)
                """, userId, name, jersey, type);
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId);
    }

    protected Long createPlayerUser(String phone, String name, int jersey, int type) {
        Long userId = insertUser(phone, "PLAYER");
        insertPlayer(userId, name, jersey, type);
        return userId;
    }

    protected Cookie cLogin(String phone, String password) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode(password), phone);
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

    protected Cookie bAdminLogin(String phone) throws Exception {
        return bLogin(phone, "ADMIN");
    }

    protected Cookie bLogin(String phone, String roleCode) throws Exception {
        insertUser(phone, roleCode);
        return bLoginExisting(phone);
    }

    /** 为已存在的账号设置后台密码并登录，避免重复建号。 */
    protected Cookie bLoginExisting(String phone) throws Exception {
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE phone = ?",
                passwordEncoder.encode("Admin123!"), phone);
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

    protected Long userId(String phone) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE phone = ?", Long.class, phone);
    }

    protected Long playerId(String phone) {
        return jdbc.queryForObject("SELECT id FROM player WHERE user_id = ?", Long.class, userId(phone));
    }

    protected Long roleId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM app_role WHERE team_id = 1 AND code = ?", Long.class, code);
    }

    protected String idemKey() {
        return UUID.randomUUID().toString();
    }

    /** 相对当前时间构造时间，避免测试随日历翻转。 */
    protected String futureAt(int plusDays, int hour) {
        return ShanghaiTime.now().plusDays(plusDays).withHour(hour).withMinute(0)
                .withSecond(0).withNano(0).toString();
    }

    protected String pastAt(int minusDays, int hour) {
        return ShanghaiTime.now().minusDays(minusDays).withHour(hour).withMinute(0)
                .withSecond(0).withNano(0).toString();
    }
}
