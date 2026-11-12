package com.yunhefc.common.idempotency;

import com.yunhefc.common.retention.RetentionTask;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(6)
@RequiredArgsConstructor
public class IdempotencyRetentionAdapter implements RetentionTask {

    private final IdempotencyKeyMapper idempotencyKeyMapper;

    @Override
    public String name() {
        return "idempotency";
    }

    @Override
    public int deleteBatch(Instant now, int limit) {
        return idempotencyKeyMapper.deleteExpiredBefore(now, limit);
    }
}
