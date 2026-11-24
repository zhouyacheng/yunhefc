package com.yunhefc;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 活动测试数据的直接落库夹具，供活动与账本集成测试共用。
 */
public final class ActivityFixtures {

    private ActivityFixtures() {
    }

    public static Long insert(JdbcTemplate jdbc, String title, String startAt, int status,
                              Integer capacity, String deadline, long teamId) {
        jdbc.update("""
                INSERT INTO team_activity (team_id, title, type, start_at, end_at, signup_deadline,
                                           capacity, family_allowed, location, status, version, created_by)
                VALUES (?, ?, 0, ?, ?, ?, ?, 1, '测试地点', ?, 0, 1)
                """, teamId, title, startAt.replace('T', ' '), startAt.replace('T', ' '),
                deadline.replace('T', ' '), capacity, status);
        return jdbc.queryForObject(
                "SELECT id FROM team_activity WHERE title = ?", Long.class, title);
    }
}
