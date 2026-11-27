package com.yunhefc.infra.outbox;

import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.common.time.ShanghaiTime;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LEASE_SECONDS = 60;
    private static final int ERROR_MAX_LENGTH = 500;

    private final OutboxEventMapper outboxEventMapper;
    private final OutboxEventDispatcher dispatcher;
    private final OutboxProperties outboxProperties;
    private final String owner = UUID.randomUUID().toString();

    @PostConstruct
    void validateDispatchTimeout() {
        long timeout = outboxProperties.getDispatchTimeoutMs();
        long leaseMillis = TimeUnit.SECONDS.toMillis(LEASE_SECONDS);
        if (timeout <= 0 || timeout >= leaseMillis) {
            throw new IllegalStateException(
                    "yunhefc.outbox.dispatch-timeout-ms 必须大于 0 且小于 " + leaseMillis);
        }
    }

    public void relay() {
        if (!outboxProperties.isEnabled()) {
            return;
        }
        List<OutboxEvent> events = outboxEventMapper.findClaimable(BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        // 整批共享一个截止时间（含提交阶段），避免逐条等待叠加；超时必须小于租约 60 秒。
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(outboxProperties.getDispatchTimeoutMs());
        List<PendingDispatch> pending = new ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            if (outboxEventMapper.claim(event.getId(), owner,
                    ShanghaiTime.now().plusSeconds(LEASE_SECONDS)) == 0) {
                continue;
            }
            long startedNanos = System.nanoTime();
            try {
                pending.add(new PendingDispatch(event, dispatcher.dispatch(event), startedNanos));
            } catch (RuntimeException ex) {
                markFailed(event, ex, startedNanos);
            }
        }

        for (PendingDispatch dispatch : pending) {
            long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
            try {
                dispatch.future().get(remainingNanos, TimeUnit.NANOSECONDS);
                outboxEventMapper.markSent(dispatch.event().getId());
            } catch (TimeoutException ex) {
                markFailed(dispatch.event(), new IllegalStateException("投递超时", ex),
                        dispatch.startedNanos());
            } catch (ExecutionException ex) {
                markFailed(dispatch.event(), unwrap(ex), dispatch.startedNanos());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                markFailed(dispatch.event(), ex, dispatch.startedNanos());
                break;
            }
        }
    }

    private void markFailed(OutboxEvent event, Exception ex, long startedNanos) {
        int attempts = event.getAttempts() == null ? 1 : event.getAttempts() + 1;
        long delay = Math.min(300, 30L * attempts);
        String error = truncate(ex.getMessage());
        outboxEventMapper.markFailed(event.getId(), ShanghaiTime.now().plusSeconds(delay),
                error, MAX_ATTEMPTS);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        log.warn("Outbox event failed, outboxId={}, eventId={}, eventType={}, attempts={}, "
                        + "elapsedMs={}, error={}",
                event.getId(), event.getEventId(), event.getEventType(), attempts, elapsedMs, error);
    }

    private Exception unwrap(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof Exception exception ? exception : ex;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > ERROR_MAX_LENGTH ? value.substring(0, ERROR_MAX_LENGTH) : value;
    }

    private record PendingDispatch(OutboxEvent event, CompletableFuture<Void> future,
                                   long startedNanos) {
    }
}
