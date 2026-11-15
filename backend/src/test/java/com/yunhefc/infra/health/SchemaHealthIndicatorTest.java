package com.yunhefc.infra.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaHealthIndicatorTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SchemaHealthIndicator indicator = new SchemaHealthIndicator(jdbc);

    @Test
    void upWhenHistoryAndCoreTableExist() {
        when(jdbc.queryForList(anyString(), eq(Integer.class))).thenReturn(List.of(1));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenFlywayHistoryIsEmpty() {
        when(jdbc.queryForList(anyString(), eq(Integer.class))).thenReturn(List.of());

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "flyway_history_empty");
    }

    @Test
    void downWhenSchemaUnavailable() {
        when(jdbc.queryForList(anyString(), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("Unknown database"));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("reason", "schema_unavailable");
    }
}
