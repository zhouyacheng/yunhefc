package com.yunhefc.infra.outbox;

import com.yunhefc.common.config.RetentionProperties;
import com.yunhefc.common.retention.RetentionTask;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Order(5)
@RequiredArgsConstructor
public class ConsumedEventRetentionAdapter implements RetentionTask {

    private final ConsumedEventMapper consumedEventMapper;
    private final RetentionProperties retentionProperties;

    @Override
    public String name() {
        return "consumed-event";
    }

    @Override
    public int deleteBatch(Instant now, int limit) {
        return consumedEventMapper.deleteProcessedBefore(
                now.minus(Duration.ofDays(retentionProperties.getConsumedEventDays())), limit);
    }
}
