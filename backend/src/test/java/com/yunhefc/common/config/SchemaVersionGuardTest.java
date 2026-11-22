package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

class SchemaVersionGuardTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void passesWhenAppliedVersionMatchesRequired() {
        SchemaVersionGuard guard = guard();
        stubApplied(guard.highestBundledVersion());

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void passesWhenDatabaseIsAheadOfCode() {
        SchemaVersionGuard guard = guard();
        stubApplied(guard.highestBundledVersion() + 1);

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void failsWhenDatabaseIsBehindCode() {
        SchemaVersionGuard guard = guard();
        stubApplied(guard.highestBundledVersion() - 1);

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("落后");
    }

    @Test
    void failsWhenNoSuccessfulMigrationExists() {
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());

        assertThatThrownBy(guard()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flyway migrate");
    }

    private SchemaVersionGuard guard() {
        return new SchemaVersionGuard(jdbc, new PathMatchingResourcePatternResolver());
    }

    private void stubApplied(int version) {
        when(jdbc.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(String.valueOf(version)));
    }
}
