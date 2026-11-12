package com.yunhefc.infra.outbox;

import com.yunhefc.common.config.RetentionProperties;
import com.yunhefc.common.retention.RetentionTask;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Order(4)
@RequiredArgsConstructor
public class OutboxFailedRetentionAdapter implements RetentionTask {

    private static final Logger log = LoggerFactory.getLogger(OutboxFailedRetentionAdapter.class);

    private final OutboxEventMapper outboxEventMapper;
    private final RetentionProperties retentionProperties;

    @Override
    public String name() {
        return "outbox-failed";
    }

    @Override
    public int deleteBatch(Instant now, int limit) {
        Instant cutoff = now.minus(Duration.ofDays(retentionProperties.getOutboxDays()));
        int due = outboxEventMapper.countFailedBefore(cutoff);
        if (due > 0) {
            log.warn("outbox FAILED events due for retention purge, count={}", due);
        }
        return outboxEventMapper.deleteFailedBefore(cutoff, limit);
    }
}
