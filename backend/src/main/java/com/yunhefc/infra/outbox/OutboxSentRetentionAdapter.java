package com.yunhefc.infra.outbox;

import com.yunhefc.common.config.RetentionProperties;
import com.yunhefc.common.retention.RetentionTask;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Order(3)
@RequiredArgsConstructor
public class OutboxSentRetentionAdapter implements RetentionTask {

    private final OutboxEventMapper outboxEventMapper;
    private final RetentionProperties retentionProperties;

    @Override
    public String name() {
        return "outbox-sent";
    }

    @Override
    public int deleteBatch(Instant now, int limit) {
        return outboxEventMapper.deleteSentBefore(
                now.minus(Duration.ofDays(retentionProperties.getOutboxDays())), limit);
    }
}
