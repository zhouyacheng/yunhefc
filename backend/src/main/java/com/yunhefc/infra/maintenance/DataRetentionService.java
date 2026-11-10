package com.yunhefc.infra.maintenance;

import com.yunhefc.common.idempotency.IdempotencyKeyMapper;
import com.yunhefc.infra.outbox.ConsumedEventMapper;
import com.yunhefc.infra.outbox.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 按 04 §10 的保留策略清理运行期数据：过期幂等键、消费去重、已发送 Outbox 保留 7 天。
 */
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);
    private static final Duration PROCESSED_RETENTION = Duration.ofDays(7);
    private static final Duration SENT_OUTBOX_RETENTION = Duration.ofDays(7);

    private final IdempotencyKeyMapper idempotencyKeyMapper;
    private final ConsumedEventMapper consumedEventMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final Clock clock;

    public void cleanup() {
        Instant now = Instant.now(clock);
        int idempotency = idempotencyKeyMapper.deleteExpiredBefore(now);
        int consumed = consumedEventMapper.deleteProcessedBefore(now.minus(PROCESSED_RETENTION));
        int outbox = outboxEventMapper.deleteSentBefore(now.minus(SENT_OUTBOX_RETENTION));
        log.debug("data retention cleanup done, idempotency={}, consumed={}, outbox={}",
                idempotency, consumed, outbox);
    }
}
