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

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.redis.enabled=false")
@Testcontainers
class FlywayMigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("yunhefc")
            .withUsername("yunhefc")
            .withPassword("yunhefc");

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
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")).isEqualTo(5);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ_%'
                """)).isEqualTo(27);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name LIKE 'QRTZ_%'
                """)).isEqualTo(11);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'FOREIGN KEY'
                  AND table_name NOT LIKE 'QRTZ_%'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK'
                """)).isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ_%'
                  AND table_comment <> ''
                """)).isEqualTo(27);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name <> 'flyway_schema_history'
                  AND table_name NOT LIKE 'QRTZ_%'
                  AND (column_comment IS NULL OR column_comment = '')
                """)).isZero();

        assertThat(count("SELECT COUNT(*) FROM team")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM app_role")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM permission")).isEqualTo(28);
        assertThat(count("SELECT COUNT(*) FROM role_permission")).isEqualTo(40);
        assertThat(count("SELECT COUNT(*) FROM system_setting")).isEqualTo(9);
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
