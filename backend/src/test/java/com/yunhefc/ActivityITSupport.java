package com.yunhefc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.yunhefc.common.time.ShanghaiTime;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

/**
 * 活动集成测试的账号与数据构造工具；每个测试方法由父类重置数据库，无需清理。
 */
abstract class ActivityITSupport extends IntegrationTestSupport {

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

    protected ResultActions createActivity(Cookie admin, String title, String startAt,
                                          Integer capacity) throws Exception {
        String endAt = java.time.LocalDateTime.parse(startAt).plusHours(2).toString();
        String body = """
                {"title":"%s","type":0,"startAt":"%s","endAt":"%s",
                 "signupDeadline":"%s","capacity":%s,"costPerPerson":88.50,
                 "location":"队内餐厅","description":"季度聚餐"}
                """.formatted(title, startAt, endAt, startAt, capacity);
        return createActivity(admin, body);
    }

    protected ResultActions createActivity(Cookie admin, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/b/activities")
                        .cookie(admin)
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    protected Long activityId(ResultActions actions) throws Exception {
        return activityId(actions.andReturn());
    }

    protected Long activityId(MvcResult result) throws Exception {
        return JsonPath.<Number>read(result.getResponse().getContentAsString(), "$.data.id").longValue();
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

    /** 相对当前时间构造活动时间，避免测试随日历翻转。 */
    protected String futureAt(int plusDays, int hour) {
        return ShanghaiTime.now().plusDays(plusDays).withHour(hour).withMinute(0)
                .withSecond(0).withNano(0).toString();
    }

    protected String pastAt(int minusDays, int hour) {
        return ShanghaiTime.now().minusDays(minusDays).withHour(hour).withMinute(0)
                .withSecond(0).withNano(0).toString();
    }

    protected Long insertActivity(String title, String startAt, int status, Integer capacity,
                                  String deadline, long teamId) {
        jdbc.update("""
                INSERT INTO team_activity (team_id, title, type, start_at, end_at, signup_deadline,
                                           capacity, location, status, version, created_by)
                VALUES (?, ?, 0, ?, ?, ?, ?, '测试地点', ?, 0, 1)
                """, teamId, title, startAt.replace('T', ' '), startAt.replace('T', ' '),
                deadline.replace('T', ' '), capacity, status);
        return jdbc.queryForObject(
                "SELECT id FROM team_activity WHERE title = ?", Long.class, title);
    }
}
