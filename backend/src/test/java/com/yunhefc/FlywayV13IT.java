package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 必须使用全新空库：先迁到 V12 造带公众号 openid 的账号，再迁到 V13 验证只增列、不动旧数据。
 */
@Testcontainers
class FlywayV13IT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            .withReuse(false);

    @Test
    void addsMpOpenidWithoutTouchingLegacyOpenid() throws Exception {
        migrateTo("12");

        try (Connection connection = connect()) {
            insertUser(connection, 9301, "13900009301", "legacy-openid");
            insertUser(connection, 9302, "13900009302", null);
        }

        migrateTo(null);

        try (Connection connection = connect()) {
            assertThat(schemaVersion(connection)).isEqualTo("18");
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM app_user WHERE id = 9301 AND openid = 'legacy-openid'
                    """)).isEqualTo(1);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM app_user WHERE id = 9301 AND mp_openid IS NULL
                    """)).isEqualTo(1);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'app_user'
                      AND index_name = 'uk_user_mp_openid' AND non_unique = 0
                    """)).isEqualTo(1);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                      AND table_name <> 'flyway_schema_history'
                      AND table_name NOT LIKE 'QRTZ\\_%'
                    """)).isEqualTo(32);

            // 唯一键对 NULL 宽容，对非空值严格。
            updateMpOpenid(connection, 9301, "mp-1");
            updateMpOpenid(connection, 9302, null);
            assertThatThrownBy(() -> updateMpOpenid(connection, 9302, "mp-1"))
                    .hasMessageContaining("uk_user_mp_openid");
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

    private void insertUser(Connection connection, long id, String phone, String openid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_user (id, phone, openid, status) VALUES (?, ?, ?, 1)")) {
            statement.setLong(1, id);
            statement.setString(2, phone);
            statement.setString(3, openid);
            statement.executeUpdate();
        }
    }

    private void updateMpOpenid(Connection connection, long id, String mpOpenid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE app_user SET mp_openid = ? WHERE id = ?")) {
            statement.setString(1, mpOpenid);
            statement.setLong(2, id);
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
