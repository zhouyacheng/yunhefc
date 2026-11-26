package com.yunhefc;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 必须使用全新空库：先迁到 V5 造存量数据，再迁移到 V6/V7 验证回填范围；V10 清理分组权重；
 * V12 清理无手机号账号并恢复 phone NOT NULL。
 */
@Testcontainers
class FlywayBackfillIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            .withReuse(false);

    @Test
    void backfillsOnlyFutureNonTerminalMatchesToMatchDayOneOClock() throws Exception {
        migrateTo("5");

        try (Connection connection = connect()) {
            insertMatch(connection, 1, "未来报名中", 0, 0,
                    "2099-07-01 16:00:00.000", "2099-07-02 02:00:00.000",
                    "2099-07-02 12:00:00.000", "2099-07-02 14:00:00.000");
            insertMatch(connection, 2, "未来已延期", 5, 0,
                    "2099-07-02 16:00:00.000", "2099-07-03 02:00:00.000",
                    "2099-07-03 12:00:00.000", "2099-07-03 14:00:00.000");
            insertMatch(connection, 3, "未来已取消", 6, 0,
                    "2099-07-03 16:00:00.000", "2099-07-04 02:00:00.000",
                    "2099-07-04 12:00:00.000", "2099-07-04 14:00:00.000");
            insertMatch(connection, 4, "已结束", 4, 0,
                    "2099-07-04 16:00:00.000", "2099-07-05 02:00:00.000",
                    "2099-07-05 12:00:00.000", "2099-07-05 14:00:00.000");
            insertMatch(connection, 5, "进行中", 3, 0,
                    "2099-07-05 16:00:00.000", "2099-07-06 02:00:00.000",
                    "2099-07-06 12:00:00.000", "2099-07-06 14:00:00.000");
            insertMatch(connection, 6, "已伪删除", 0, 1,
                    "2099-07-06 16:00:00.000", "2099-07-07 02:00:00.000",
                    "2099-07-07 12:00:00.000", "2099-07-07 14:00:00.000");
            insertMatch(connection, 7, "非法凌晨场", 0, 0,
                    "2099-07-07 16:00:00.000", "2099-07-08 02:00:00.000",
                    "2099-07-08 16:30:00.000", "2099-07-08 18:30:00.000");
            insertMatch(connection, 8, "已正确", 0, 0,
                    "2099-07-08 16:00:00.000", "2099-07-08 17:00:00.000",
                    "2099-07-09 12:00:00.000", "2099-07-09 14:00:00.000");
        }

        migrateTo(null);

        try (Connection connection = connect()) {
            assertThat(lockOf(connection, 1)).isEqualTo("2099-07-01 17:00:00");
            assertThat(lockOf(connection, 2)).isEqualTo("2099-07-02 17:00:00");
            assertThat(lockOf(connection, 3)).isEqualTo("2099-07-03 17:00:00");
            assertThat(lockOf(connection, 4)).isEqualTo("2099-07-05 02:00:00");
            assertThat(lockOf(connection, 5)).isEqualTo("2099-07-06 02:00:00");
            assertThat(lockOf(connection, 6)).isEqualTo("2099-07-07 02:00:00");
            assertThat(lockOf(connection, 7)).isEqualTo("2099-07-08 02:00:00");
            assertThat(lockOf(connection, 8)).isEqualTo("2099-07-08 17:00:00");
            assertThat(schemaVersion(connection)).isEqualTo("21");
        }
    }

    private void migrateTo(String version) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        if (version != null) {
            configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private void insertMatch(Connection connection, int matchNo, String title, int status,
                             int deleted, String signupDeadline, String cancelLock,
                             String startAt, String endAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by, is_deleted)
                VALUES (1, ?, 1, ?, ?, ?, ?, '测试场地', '2099', '橙队', '兰队', ?, 0, 30, ?, ?, 1, ?)
                """)) {
            statement.setString(1, title);
            statement.setString(2, startAt);
            statement.setString(3, endAt);
            statement.setString(4, signupDeadline);
            statement.setString(5, cancelLock);
            statement.setInt(6, status);
            statement.setInt(7, matchNo);
            statement.setInt(8, matchNo);
            statement.setInt(9, deleted);
            statement.executeUpdate();
        }
    }

    private String lockOf(Connection connection, int matchNo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT DATE_FORMAT(cancel_lock, '%Y-%m-%d %H:%i:%s')
                FROM team_match WHERE total_match_no = ?
                """)) {
            statement.setInt(1, matchNo);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private String schemaVersion(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT version FROM flyway_schema_history
                     WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1
                     """)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }
}
