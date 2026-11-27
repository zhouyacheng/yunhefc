package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 必须使用全新空库：从生产当前版本 V19 升到 V21，验证 V20（内容四表 + 索引 + notification 注释）
 * 与 V21（内容权限）纯增量、不动存量数据，并验证重复 migrate 幂等。
 */
@Testcontainers
class FlywayContentIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            .withReuse(false);

    @Test
    void upgradesFromV19ToV21Idempotently() throws Exception {
        migrateTo("19");

        try (Connection connection = connect()) {
            insertLegacyActivity(connection);
        }

        migrateTo(null);
        MigrateResult repeat = migrateTo(null);
        assertThat(repeat.migrationsExecuted).as("重复 migrate 应为空转").isZero();

        try (Connection connection = connect()) {
            assertThat(schemaVersion(connection)).isEqualTo("21");
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM flyway_schema_history
                    WHERE success = 1 AND version IN ('20', '21')
                    """)).isEqualTo(2);

            for (String table : new String[]{
                    "content_album", "content_album_photo", "content_post", "content_post_like"}) {
                assertThat(countOf(connection, """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = '%s'
                        """.formatted(table))).isEqualTo(1);
            }
            assertThat(countOf(connection, """
                    SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name LIKE 'content\\_%'
                      AND index_name <> 'PRIMARY'
                    """)).isEqualTo(7);

            // 存量数据保持原样。
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM team_activity WHERE title = '内容迁移前活动' AND capacity = 50
                    """)).isEqualTo(1);

            assertThat(countOf(connection, "SELECT COUNT(*) FROM permission")).isEqualTo(37);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM permission WHERE code LIKE 'content:%'
                    """)).isEqualTo(4);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM role_permission rp
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE p.code LIKE 'content:%'
                      AND rp.role_id = (SELECT id FROM app_role WHERE code = 'ADMIN')
                    """)).isEqualTo(4);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM role_permission rp
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE p.code IN ('content:view:any', 'content:create:own')
                      AND rp.role_id = (SELECT id FROM app_role WHERE code = 'PLAYER')
                    """)).isEqualTo(2);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM role_permission rp
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE p.code LIKE 'content:%'
                      AND rp.role_id = (SELECT id FROM app_role WHERE code = 'GUEST')
                    """)).isZero();

            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'notification'
                      AND column_name = 'type' AND column_comment LIKE '%14-内容举报%'
                    """)).isEqualTo(1);
        }
    }

    private void insertLegacyActivity(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO team_activity
                        (team_id, title, type, start_at, signup_deadline, location, status, version, created_by)
                    VALUES
                        (1, '内容迁移前活动', 0, '2030-12-20 18:00:00.000',
                         '2030-12-20 17:00:00.000', '体育馆', 0, 0, 1)
                    """);
        }
    }

    private MigrateResult migrateTo(String version) {
        var configuration = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        if (version != null) {
            configuration.target(MigrationVersion.fromVersion(version));
        }
        return configuration.load().migrate();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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
