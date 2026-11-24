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
 * 必须使用全新空库：模拟生产从 V14 升到 V17，先造存量数据，验证 V16/V17 纯增量迁移不动旧数据。
 */
@Testcontainers
class FlywayV17UpgradeIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            .withReuse(false);

    @Test
    void upgradesFromV14WithoutTouchingLegacyData() throws Exception {
        migrateTo("14");

        try (Connection connection = connect()) {
            insertUser(connection, 9401, "13900009401", "legacy-openid");
            insertUser(connection, 9402, "13900009402", null);
        }

        migrateTo(null);

        try (Connection connection = connect()) {
            assertThat(schemaVersion(connection)).isEqualTo("17");
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM flyway_schema_history
                    WHERE success = 1 AND version IN ('16', '17')
                    """)).isEqualTo(2);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM flyway_schema_history WHERE version = '15'
                    """)).isZero();

            // 存量数据保持原样，旧镜像依赖的查询仍可用。
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM app_user
                    WHERE id = 9401 AND phone = '13900009401' AND openid = 'legacy-openid'
                    """)).isEqualTo(1);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM app_user WHERE id = 9402 AND mp_openid IS NULL
                    """)).isEqualTo(1);

            for (String table : new String[]{
                    "team_activity", "activity_signup", "ledger_category", "team_ledger_entry"}) {
                assertThat(countOf(connection, """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = '%s'
                        """.formatted(table))).isEqualTo(1);
            }

            assertThat(countOf(connection, "SELECT COUNT(*) FROM permission")).isEqualTo(33);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM permission
                    WHERE code IN ('activity:signup:own', 'activity:view:any',
                                   'activity:manage:any', 'ledger:view:any', 'ledger:manage:any')
                    """)).isEqualTo(5);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM role_permission rp
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE p.code IN ('activity:signup:own', 'activity:view:any',
                                     'activity:manage:any', 'ledger:view:any', 'ledger:manage:any')
                      AND rp.role_id = (SELECT id FROM app_role WHERE code = 'ADMIN')
                    """)).isEqualTo(5);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM role_permission rp
                    JOIN permission p ON p.id = rp.permission_id
                    WHERE p.code IN ('activity:signup:own', 'activity:view:any', 'ledger:view:any')
                      AND rp.role_id = (SELECT id FROM app_role WHERE code = 'PLAYER')
                    """)).isEqualTo(3);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM ledger_category WHERE is_deleted = 0
                    """)).isEqualTo(7);
            assertThat(countOf(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'notification'
                      AND column_name = 'type'
                      AND column_comment LIKE '%12-活动创建%' AND column_comment LIKE '%13-活动取消%'
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

    private void insertUser(Connection connection, long id, String phone, String openid)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_user (id, phone, openid, status) VALUES (?, ?, ?, 1)")) {
            statement.setLong(1, id);
            statement.setString(2, phone);
            statement.setString(3, openid);
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
