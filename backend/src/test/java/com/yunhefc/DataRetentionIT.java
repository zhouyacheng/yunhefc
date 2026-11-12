package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yunhefc.infra.maintenance.DataRetentionJob;
import com.yunhefc.infra.maintenance.DataRetentionService;
import com.yunhefc.infra.outbox.OutboxFailedRetentionAdapter;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Import(DataRetentionIT.FixedClockConfig.class)
class DataRetentionIT extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-12T03:30:00Z");

    @Autowired
    private DataRetentionService dataRetentionService;

    @Autowired
    private ApplicationContext context;

    @Test
    void removesNotificationsOlderThanRetentionButKeepsRecentOnes() {
        insertNotification(900001L, NOW.minus(91, ChronoUnit.DAYS), 0);
        insertNotification(900002L, NOW.minus(89, ChronoUnit.DAYS), 0);
        // 已软删除的过期通知也必须物理清理，证明绕过 @TableLogic。
        insertNotification(900003L, NOW.minus(91, ChronoUnit.DAYS), 1);

        dataRetentionService.cleanup();

        assertThat(notificationExists(900001L)).isFalse();
        assertThat(notificationExists(900002L)).isTrue();
        assertThat(notificationExists(900003L)).isFalse();
    }

    @Test
    void removesExpiredInvitesWithTheirUsesButKeepsRecentlyInvalidatedOnes() {
        insertInvite(800001L, 2, NOW.minus(31, ChronoUnit.DAYS), null);
        insertInvite(800002L, 2, NOW.minus(29, ChronoUnit.DAYS), null);
        insertInvite(800003L, 1, NOW.plus(1, ChronoUnit.DAYS), NOW.minus(31, ChronoUnit.DAYS));
        insertInvite(800004L, 1, NOW.plus(1, ChronoUnit.DAYS), NOW.minus(29, ChronoUnit.DAYS));
        // 还没被 InviteExpiryJob 置为失效的邀请不归保留任务处理。
        insertInvite(800005L, 0, NOW.minus(100, ChronoUnit.DAYS), null);
        insertInviteUse(800001L, 999902L);
        insertInviteUse(800002L, 999903L);

        dataRetentionService.cleanup();

        assertThat(inviteExists(800001L)).isFalse();
        assertThat(inviteExists(800002L)).isTrue();
        assertThat(inviteExists(800003L)).isFalse();
        assertThat(inviteExists(800004L)).isTrue();
        assertThat(inviteExists(800005L)).isTrue();
        assertThat(inviteUseCount(800001L)).isZero();
        assertThat(inviteUseCount(800002L)).isEqualTo(1);
    }

    @Test
    void removesSentAndFailedOutboxEventsButKeepsPendingOnes() {
        String oldSent = UUID.randomUUID().toString();
        String recentSent = UUID.randomUUID().toString();
        String oldFailed = UUID.randomUUID().toString();
        String recentFailed = UUID.randomUUID().toString();
        String oldPending = UUID.randomUUID().toString();

        insertOutbox(oldSent, 1, NOW.minus(30, ChronoUnit.DAYS), NOW.minus(8, ChronoUnit.DAYS));
        insertOutbox(recentSent, 1, NOW.minus(30, ChronoUnit.DAYS), NOW.minus(6, ChronoUnit.DAYS));
        insertOutbox(oldFailed, 2, NOW.minus(8, ChronoUnit.DAYS), null);
        insertOutbox(recentFailed, 2, NOW.minus(6, ChronoUnit.DAYS), null);
        insertOutbox(oldPending, 0, NOW.minus(30, ChronoUnit.DAYS), null);

        Logger logger = (Logger) LoggerFactory.getLogger(OutboxFailedRetentionAdapter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            dataRetentionService.cleanup();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(outboxExists(oldSent)).isFalse();
        assertThat(outboxExists(recentSent)).isTrue();
        assertThat(outboxExists(oldFailed)).isFalse();
        assertThat(outboxExists(recentFailed)).isTrue();
        assertThat(outboxExists(oldPending)).isTrue();
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("count=1"));
    }

    @Test
    void removesConsumedEventsOlderThanRetention() {
        String oldEvent = UUID.randomUUID().toString();
        String recentEvent = UUID.randomUUID().toString();
        insertConsumed(oldEvent, NOW.minus(8, ChronoUnit.DAYS), 1);
        insertConsumed(recentEvent, NOW.minus(6, ChronoUnit.DAYS), 2);

        dataRetentionService.cleanup();

        assertThat(consumedExists(oldEvent)).isFalse();
        assertThat(consumedExists(recentEvent)).isTrue();
    }

    @Test
    void removesOnlyExpiredIdempotencyKeys() {
        insertIdempotency("expired", NOW.minus(1, ChronoUnit.HOURS));
        insertIdempotency("active", NOW.plus(1, ChronoUnit.HOURS));

        dataRetentionService.cleanup();

        assertThat(idempotencyExists("expired")).isFalse();
        assertThat(idempotencyExists("active")).isTrue();
    }

    @Test
    void keepsRecordsExactlyAtRetentionBoundaryExceptExpiredIdempotencyKeys() {
        insertNotification(903001L, NOW.minus(90, ChronoUnit.DAYS), 0);
        insertInvite(803001L, 2, NOW.minus(30, ChronoUnit.DAYS), null);
        String sentAtBoundary = UUID.randomUUID().toString();
        String failedAtBoundary = UUID.randomUUID().toString();
        String consumedAtBoundary = UUID.randomUUID().toString();
        insertOutbox(sentAtBoundary, 1, NOW.minus(30, ChronoUnit.DAYS),
                NOW.minus(7, ChronoUnit.DAYS));
        insertOutbox(failedAtBoundary, 2, NOW.minus(7, ChronoUnit.DAYS), null);
        insertConsumed(consumedAtBoundary, NOW.minus(7, ChronoUnit.DAYS), 1);
        insertIdempotency("boundary", NOW);

        dataRetentionService.cleanup();

        assertThat(notificationExists(903001L)).isTrue();
        assertThat(inviteExists(803001L)).isTrue();
        assertThat(outboxExists(sentAtBoundary)).isTrue();
        assertThat(outboxExists(failedAtBoundary)).isTrue();
        assertThat(consumedExists(consumedAtBoundary)).isTrue();
        assertThat(idempotencyExists("boundary")).isFalse();
    }

    @Test
    void deletesInBatchesUntilBelowBatchSize() {
        Instant old = NOW.minus(91, ChronoUnit.DAYS);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            rows.add(new Object[]{901000L + i, Timestamp.from(old)});
        }
        jdbc.batchUpdate("""
                INSERT INTO notification (id, user_id, type, title, body, created_at)
                VALUES (?, 999901, 9, 'batch', 'batch', ?)
                """, rows);

        dataRetentionService.cleanup();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification", Integer.class)).isZero();
    }

    @Test
    void repeatedCleanupIsIdempotent() {
        insertNotification(902001L, NOW.minus(91, ChronoUnit.DAYS), 0);
        insertIdempotency("repeat", NOW.minus(1, ChronoUnit.HOURS));

        dataRetentionService.cleanup();
        dataRetentionService.cleanup();

        assertThat(notificationExists(902001L)).isFalse();
        assertThat(idempotencyExists("repeat")).isFalse();
    }

    @Test
    void quartzTriggerRunsAtHalfPastThreeInShanghai() {
        JobDetail detail = context.getBean("dataRetentionJobDetail", JobDetail.class);
        assertThat(detail.getJobClass()).isEqualTo(DataRetentionJob.class);
        assertThat(detail.getKey()).isEqualTo(JobKey.jobKey("dataRetentionJob", "yunhefc"));

        Trigger trigger = context.getBean("dataRetentionTrigger", Trigger.class);
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        CronTrigger cron = (CronTrigger) trigger;
        assertThat(cron.getCronExpression()).isEqualTo("0 30 3 * * ?");
        assertThat(cron.getTimeZone().getID()).isEqualTo("Asia/Shanghai");
        assertThat(trigger.getKey()).isEqualTo(TriggerKey.triggerKey("dataRetentionTrigger", "yunhefc"));
        assertThat(trigger.getJobKey()).isEqualTo(detail.getKey());
    }

    private void insertNotification(Long id, Instant createdAt, int isDeleted) {
        jdbc.update("""
                INSERT INTO notification (id, user_id, type, title, body, created_at, is_deleted)
                VALUES (?, 999901, 9, 'retention', 'retention', ?, ?)
                """, id, Timestamp.from(createdAt), isDeleted);
    }

    private boolean notificationExists(Long id) {
        return count("SELECT COUNT(*) FROM notification WHERE id = ?", id) > 0;
    }

    private void insertInvite(Long id, int status, Instant expiresAt, Instant revokedAt) {
        jdbc.update("""
                INSERT INTO invite_link (id, team_id, type, token_hash, inviter_user_id,
                    expires_at, status, revoked_at)
                VALUES (?, 1, 0, ?, 999901, ?, ?, ?)
                """, id, String.format("%064d", id), Timestamp.from(expiresAt), status,
                revokedAt == null ? null : Timestamp.from(revokedAt));
    }

    private boolean inviteExists(Long id) {
        return count("SELECT COUNT(*) FROM invite_link WHERE id = ?", id) > 0;
    }

    private void insertInviteUse(Long inviteId, Long userId) {
        jdbc.update("INSERT INTO invite_use (invite_id, user_id) VALUES (?, ?)", inviteId, userId);
    }

    private int inviteUseCount(Long inviteId) {
        return count("SELECT COUNT(*) FROM invite_use WHERE invite_id = ?", inviteId);
    }

    private void insertOutbox(String eventId, int status, Instant createdAt, Instant sentAt) {
        jdbc.update("""
                INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type,
                    payload_json, status, attempts, created_at, sent_at)
                VALUES (?, 'USER', 1, 'AUDIT_RECORDED', '{}', ?, 0, ?, ?)
                """, eventId, status, Timestamp.from(createdAt),
                sentAt == null ? null : Timestamp.from(sentAt));
    }

    private boolean outboxExists(String eventId) {
        return count("SELECT COUNT(*) FROM outbox_event WHERE event_id = ?", eventId) > 0;
    }

    private void insertConsumed(String eventId, Instant processedAt, long offset) {
        jdbc.update("""
                INSERT INTO consumed_event (consumer_group, event_id, topic, partition_no,
                    offset_no, processed_at)
                VALUES ('retention-test', ?, 'retention-topic', 0, ?, ?)
                """, eventId, offset, Timestamp.from(processedAt));
    }

    private boolean consumedExists(String eventId) {
        return count("SELECT COUNT(*) FROM consumed_event WHERE event_id = ?", eventId) > 0;
    }

    private void insertIdempotency(String idemKey, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO idempotency_key (user_id, endpoint, idem_key, request_hash, expires_at)
                VALUES (999901, 'RETENTION', ?, REPEAT('a', 64), ?)
                """, idemKey, Timestamp.from(expiresAt));
    }

    private boolean idempotencyExists(String idemKey) {
        return count("SELECT COUNT(*) FROM idempotency_key WHERE idem_key = ?", idemKey) > 0;
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
