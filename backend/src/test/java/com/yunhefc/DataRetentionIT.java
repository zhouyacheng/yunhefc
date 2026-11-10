package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.infra.maintenance.DataRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

class DataRetentionIT extends IntegrationTestSupport {

    @Autowired
    private DataRetentionService dataRetentionService;

    @Test
    void cleanupRemovesExpiredAndOldRecordsButKeepsFreshOnes() {
        Instant old = Instant.now().minus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
        String oldEvent = UUID.randomUUID().toString();
        String freshEvent = UUID.randomUUID().toString();

        jdbc.update("""
                INSERT INTO idempotency_key (user_id, endpoint, idem_key, request_hash, expires_at)
                VALUES (999901, 'RETENTION', 'old', REPEAT('a', 64), ?)
                """, Timestamp.from(old));
        jdbc.update("""
                INSERT INTO idempotency_key (user_id, endpoint, idem_key, request_hash, expires_at)
                VALUES (999901, 'RETENTION', 'fresh', REPEAT('b', 64), ?)
                """, Timestamp.from(future));
        jdbc.update("""
                INSERT INTO consumed_event (consumer_group, event_id, topic, partition_no, offset_no, processed_at)
                VALUES ('retention-test', ?, 'retention-topic', 0, 1, ?)
                """, oldEvent, Timestamp.from(old));
        jdbc.update("""
                INSERT INTO consumed_event (consumer_group, event_id, topic, partition_no, offset_no, processed_at)
                VALUES ('retention-test', ?, 'retention-topic', 0, 2, ?)
                """, freshEvent, Timestamp.from(Instant.now()));
        jdbc.update("""
                INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type,
                    payload_json, status, attempts, created_at, sent_at)
                VALUES (?, 'USER', 1, 'AUDIT_RECORDED', '{}', 1, 0, ?, ?)
                """, oldEvent, Timestamp.from(old), Timestamp.from(old));
        jdbc.update("""
                INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type,
                    payload_json, status, attempts, created_at, sent_at)
                VALUES (?, 'USER', 1, 'AUDIT_RECORDED', '{}', 1, 0, ?, ?)
                """, freshEvent, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        dataRetentionService.cleanup();

        assertThat(count("idempotency_key", "idem_key", "old")).isZero();
        assertThat(count("idempotency_key", "idem_key", "fresh")).isEqualTo(1);
        assertThat(count("consumed_event", "event_id", oldEvent)).isZero();
        assertThat(count("consumed_event", "event_id", freshEvent)).isEqualTo(1);
        assertThat(count("outbox_event", "event_id", oldEvent)).isZero();
        assertThat(count("outbox_event", "event_id", freshEvent)).isEqualTo(1);
    }

    private Integer count(String table, String column, String value) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }
}
