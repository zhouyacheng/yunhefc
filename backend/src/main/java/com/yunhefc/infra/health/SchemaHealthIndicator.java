package com.yunhefc.infra.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * readiness 的内置 db 指示器只执行 SELECT 1，库被删除后旧连接仍能通过，
 * 因此额外校验 Flyway 历史与一张基础业务表，表缺失即判定 DOWN。
 */
@Component("schema")
class SchemaHealthIndicator implements HealthIndicator {

    private static final String HISTORY_SQL =
            "SELECT 1 FROM flyway_schema_history WHERE success = 1 LIMIT 1";
    private static final String CORE_TABLE_SQL = "SELECT 1 FROM team LIMIT 1";

    private final JdbcTemplate jdbc;

    SchemaHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Health health() {
        try {
            if (jdbc.queryForList(HISTORY_SQL, Integer.class).isEmpty()) {
                return Health.down().withDetail("reason", "flyway_history_empty").build();
            }
            jdbc.queryForList(CORE_TABLE_SQL, Integer.class);
            return Health.up().build();
        } catch (DataAccessException ex) {
            return Health.down().withDetail("reason", "schema_unavailable").build();
        }
    }
}
