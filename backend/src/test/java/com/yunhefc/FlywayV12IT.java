package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * 必须使用全新空库：先迁到 V11 造无手机号账号，再迁移到 V12 验证级联清理与 phone NOT NULL。
 */
@Testcontainers
class FlywayV12IT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            .withReuse(false);

    @Test
    void removesNullPhoneAccountsAndPreservesRetainedEvents() throws Exception {
        migrateTo("11");

        try (Connection connection = connect()) {
            insertUser(connection, 9101, null, 0);
            insertUser(connection, 9102, "13900009102", 1);
            insertUser(connection, 9103, "13900009103", 1);
            insertPlayer(connection, 9101, 9101, 2);
            insertPlayer(connection, 9102, 9102, 1);
            insertPlayer(connection, 9103, 9103, 3);
            insertMatch(connection, 101);
            long matchId = matchIdOf(connection, 101);
            insertRegistration(connection, matchId, 9101);
            insertEvent(connection, 9201, matchId, 0, 9101L, null, null, null);
            insertEvent(connection, 9202, matchId, 0, 9102L, 9101L, null, null);
            insertEvent(connection, 9203, matchId, 2, null, null, 9101L, 9102L);
            insertEvent(connection, 9204, matchId, 2, null, null, 9103L, 9102L);
            insertEvent(connection, 9205, matchId, 0, 9102L, null, null, null);
            insertEventHistory(connection, 9201);
            insertEventHistory(connection, 9202);
        }

        migrateTo(null);

        try (Connection connection = connect()) {
            assertThat(schemaVersion(connection)).isEqualTo("21");
            assertThat(countOf(connection, "SELECT COUNT(*) FROM app_user WHERE id = 9101")).isZero();
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM app_user WHERE id IN (9102, 9103)")).isEqualTo(2);
            assertThat(countOf(connection, "SELECT COUNT(*) FROM player WHERE id = 9101")).isZero();
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM player WHERE id IN (9102, 9103)")).isEqualTo(2);
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM registration WHERE player_id = 9101")).isZero();

            // 主体球员被删、换人参与人被删的事件整体删除，事件历史一并清理。
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM match_event WHERE id IN (9201, 9203)")).isZero();
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM event_history WHERE event_id = 9201")).isZero();

            // 仍有效球员的事件保留；仅助攻被删时置空助攻。
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM match_event WHERE id IN (9202, 9204, 9205)")).isEqualTo(3);
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM match_event WHERE id = 9202 AND assist_player_id IS NULL"))
                    .isEqualTo(1);
            assertThat(countOf(connection,
                    "SELECT COUNT(*) FROM event_history WHERE event_id = 9202")).isEqualTo(1);

            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'app_user'
                      AND column_name = 'phone' AND is_nullable = 'NO'
                    """)).isEqualTo(1);
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

    private void insertUser(Connection connection, long id, String phone, int status) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_user (id, phone, status) VALUES (?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, phone);
            statement.setInt(3, status);
            statement.executeUpdate();
        }
    }

    private void insertPlayer(Connection connection, long id, long userId, int position) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player (id, team_id, user_id, name, jersey, type, position,
                    secondary_positions, skill)
                VALUES (?, 1, ?, ?, NULL, 0, ?, '[]', 3.00)
                """)) {
            statement.setLong(1, id);
            statement.setLong(2, userId);
            statement.setString(3, "V12球员" + id);
            statement.setInt(4, position);
            statement.executeUpdate();
        }
    }

    private void insertMatch(Connection connection, int matchNo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO team_match (team_id, title, type, start_at, end_at, signup_deadline,
                    cancel_lock, venue, season, home_team_name, away_team_name, status,
                    manual_status, capacity, season_match_no, total_match_no, created_by, is_deleted)
                VALUES (1, 'V12 清理', 1, '2099-08-01 12:00:00', '2099-08-01 14:00:00',
                    '2099-08-01 16:00:00', '2099-08-01 18:00:00', '测试场地', '2099',
                    '橙队', '兰队', 0, 0, 30, ?, ?, 1, 0)
                """)) {
            statement.setInt(1, matchNo);
            statement.setInt(2, matchNo);
            statement.executeUpdate();
        }
    }

    private long matchIdOf(Connection connection, int matchNo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM team_match WHERE total_match_no = ?")) {
            statement.setInt(1, matchNo);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private void insertRegistration(Connection connection, long matchId, long playerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO registration (match_id, player_id, type, status, registered_at)
                VALUES (?, ?, 0, 0, NOW())
                """)) {
            statement.setLong(1, matchId);
            statement.setLong(2, playerId);
            statement.executeUpdate();
        }
    }

    private void insertEvent(Connection connection, long id, long matchId, int type,
                             Long playerId, Long assistPlayerId,
                             Long inPlayerId, Long outPlayerId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO match_event (id, match_id, team, type, player_id, assist_player_id,
                    in_player_id, out_player_id, minute, half, created_by)
                VALUES (?, ?, 0, ?, ?, ?, ?, ?, 10, 0, 9102)
                """)) {
            statement.setLong(1, id);
            statement.setLong(2, matchId);
            statement.setInt(3, type);
            statement.setObject(4, playerId);
            statement.setObject(5, assistPlayerId);
            statement.setObject(6, inPlayerId);
            statement.setObject(7, outPlayerId);
            statement.executeUpdate();
        }
    }

    private void insertEventHistory(Connection connection, long eventId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO event_history (event_id, action, actor_user_id)
                VALUES (?, 0, 9102)
                """)) {
            statement.setLong(1, eventId);
            statement.executeUpdate();
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

    private int countOf(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
