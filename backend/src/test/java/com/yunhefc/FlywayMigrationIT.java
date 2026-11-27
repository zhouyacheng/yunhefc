package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 必须使用全新空库：断言迁移历史与种子数据精确计数，不要并入共享容器基类。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.health.redis.enabled=false", "yunhefc.outbox.enabled=false", "yunhefc.sms.mock-enabled=true", "yunhefc.wechat.enabled=true", "yunhefc.wechat.mode=mock", "yunhefc.wechat.require-wechat-ua=false"})
@Testcontainers
class FlywayMigrationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc")
            .withReuse(false);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestRestTemplate rest;

    @Test
    void migratesEmptyDatabaseAndSeedsReferenceData() {
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")).isEqualTo(20);
        assertThat(count("""
                SELECT COUNT(*) FROM team
                WHERE id = 1 AND ABS(TIMESTAMPDIFF(MINUTE, created_at, NOW())) <= 5
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ\\_%'
                """)).isEqualTo(36);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name LIKE 'QRTZ_%'
                """)).isEqualTo(11);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND column_name = 'phone' AND is_nullable = 'NO'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND column_name = 'mp_openid' AND is_nullable = 'YES'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND index_name = 'uk_user_mp_openid' AND non_unique = 0
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'FOREIGN KEY'
                  AND table_name NOT LIKE 'QRTZ\\_%'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(DISTINCT table_name, index_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND index_name <> 'PRIMARY'
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ\\_%'
                """)).isEqualTo(80);

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ\\_%'
                  AND (table_comment IS NULL OR table_comment = '')
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ\\_%'
                  AND (column_comment IS NULL OR column_comment = '')
                """)).isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'player'
                  AND column_name = 'skill' AND column_type = 'decimal(3,2)'
                  AND is_nullable = 'NO' AND column_default = '3.00'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'match_grouping_player'
                  AND column_name = 'manual_score' AND column_type = 'decimal(3,2)'
                  AND is_nullable = 'YES'
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'player_stats_summary'
                  AND column_name IN ('internal_appearances', 'internal_wins', 'own_goals')
                  AND column_type = 'int unsigned' AND is_nullable = 'NO'
                  AND column_default = '0' AND column_comment <> ''
                """)).isEqualTo(3);

        assertThat(count("SELECT COUNT(*) FROM team")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM app_role")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM permission")).isEqualTo(37);
        assertThat(count("SELECT COUNT(*) FROM role_permission")).isEqualTo(54);
        assertThat(count("SELECT COUNT(*) FROM ledger_category WHERE is_deleted = 0")).isEqualTo(7);
        assertThat(count("""
                SELECT COUNT(*) FROM ledger_category
                WHERE name = '其他' AND direction IS NULL
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notification'
                  AND column_name = 'type'
                  AND column_comment LIKE '%12-活动创建%' AND column_comment LIKE '%13-活动取消%'
                """)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM system_setting WHERE is_deleted = 0")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM formation_template WHERE is_preset = 1")).isEqualTo(8);
        assertThat(count("SELECT COUNT(*) FROM formation_slot")).isEqualTo(64);
    }

    @Test
    void healthEndpointIsUp() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
