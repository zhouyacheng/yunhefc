package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.OutboxEventDispatcher;
import com.yunhefc.infra.outbox.OutboxRelay;
import com.yunhefc.infra.outbox.OutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

class OutboxDispatchIT extends IntegrationTestSupport {

    @Autowired
    OutboxRelay outboxRelay;

    @Autowired
    OutboxService outboxService;

    @Autowired
    OutboxProperties outboxProperties;

    @BeforeEach
    void enableRelay() {
        // 基类关闭中继以避免后台任务干扰；这里手工触发 relay，验证完整投递链路。
        outboxProperties.setEnabled(true);
        outboxProperties.setDispatchTimeoutMs(30_000);
    }

    @AfterEach
    void disableRelay() {
        outboxProperties.setEnabled(false);
    }

    @Test
    void deliversAuditEventAndMarksOutboxSent() {
        outboxService.append(1L, "MATCH", 42L, EventTypes.AUDIT_RECORDED, auditPayload());

        outboxRelay.relay();

        Map<String, Object> outbox = jdbc.queryForMap("SELECT id, status FROM outbox_event");
        assertThat(number(outbox.get("status"))).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isEqualTo(1);

        Map<String, Object> consumed = jdbc.queryForMap(
                "SELECT consumer_group, topic, partition_no, offset_no FROM consumed_event");
        assertThat(consumed.get("consumer_group")).isEqualTo("yunhefc-audit");
        assertThat(consumed.get("topic")).isEqualTo(OutboxEventDispatcher.AUDIT_CHANNEL);
        assertThat(number(consumed.get("partition_no"))).isEqualTo(0);
        assertThat(number(consumed.get("offset_no"))).isEqualTo(number(outbox.get("id")));
    }

    @Test
    void deliversNotificationEvent() {
        outboxService.append(1L, "USER", 9L, EventTypes.ACCOUNT_APPROVED, Map.of("userId", 9L));

        outboxRelay.relay();

        assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id = 9 AND type = 10")).isEqualTo(1);
        assertThat(number(jdbc.queryForObject("SELECT status FROM outbox_event", Integer.class))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT topic FROM consumed_event", String.class))
                .isEqualTo(OutboxEventDispatcher.NOTIFICATION_CHANNEL);
    }

    @Test
    void repeatedDispatchIsDeduplicated() {
        outboxService.append(1L, "MATCH", 42L, EventTypes.AUDIT_RECORDED, auditPayload());
        outboxRelay.relay();
        jdbc.update("""
                UPDATE outbox_event
                SET status = 0, sent_at = NULL, lease_owner = NULL, lease_until = NULL
                """);

        outboxRelay.relay();

        assertThat(count("SELECT COUNT(*) FROM audit_log")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM consumed_event")).isEqualTo(1);
        assertThat(number(jdbc.queryForObject("SELECT status FROM outbox_event", Integer.class))).isEqualTo(1);
    }

    @Test
    void unknownEventTypeFailsAfterMaxAttempts() {
        outboxService.append(1L, "MATCH", 42L, "UNKNOWN_EVENT", Map.of("x", 1));

        for (int attempt = 1; attempt <= 5; attempt++) {
            makeClaimableAgain();
            outboxRelay.relay();
        }

        Map<String, Object> outbox = jdbc.queryForMap("SELECT status, attempts, last_error FROM outbox_event");
        assertThat(number(outbox.get("status"))).isEqualTo(2);
        assertThat(number(outbox.get("attempts"))).isEqualTo(5);
        assertThat(String.valueOf(outbox.get("last_error"))).contains("无对应投递通道");
    }

    @Test
    void handlerFailureRollsBackConsumedEventAndRetries() {
        outboxService.append(1L, "MATCH", 42L, EventTypes.AUDIT_RECORDED,
                Map.of("teamId", 1L, "action", "BROKEN", "targetType", "MATCH",
                        "targetId", 42L, "beforeJson", "not-json"));

        outboxRelay.relay();

        Map<String, Object> outbox = jdbc.queryForMap("SELECT status, attempts FROM outbox_event");
        assertThat(number(outbox.get("status"))).isEqualTo(0);
        assertThat(number(outbox.get("attempts"))).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM consumed_event")).isZero();
        assertThat(count("SELECT COUNT(*) FROM audit_log")).isZero();
    }

    private void makeClaimableAgain() {
        jdbc.update("""
                UPDATE outbox_event
                SET next_retry_at = NOW(3) - INTERVAL 1 SECOND, lease_owner = NULL, lease_until = NULL
                """);
    }

    private Map<String, Object> auditPayload() {
        return Map.of(
                "teamId", 1L,
                "actorUserId", 1L,
                "actorName", "测试管理员",
                "action", "TEST_ACTION",
                "targetType", "MATCH",
                "targetId", 42L,
                "beforeJson", "{}",
                "afterJson", "{}",
                "requestId", "req-outbox-it",
                "ip", "127.0.0.1");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
