package com.yunhefc.infra.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yunhefc.common.retention.RetentionTask;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

class DataRetentionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T03:30:00Z"), ZoneOffset.UTC);
    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(DataRetentionService.class);

    @Test
    void deletesEachTaskInBatchesOfOneThousandUntilBelowBatchSize() {
        StubTask notification = new StubTask("notification", 1000, 1000, 500);
        StubTask idempotency = new StubTask("idempotency", 0);

        new DataRetentionService(List.of(notification, idempotency), CLOCK).cleanup();

        assertThat(notification.limits).containsExactly(1000, 1000, 1000);
        assertThat(idempotency.limits).containsExactly(1000);
    }

    @Test
    void logsDeletedCountAtDebug() {
        StubTask notification = new StubTask("notification", 1000, 500);
        ListAppender<ILoggingEvent> appender = attachAppender();
        Level previous = LOGGER.getLevel();
        LOGGER.setLevel(Level.DEBUG);

        try {
            new DataRetentionService(List.of(notification), CLOCK).cleanup();
        } finally {
            LOGGER.setLevel(previous);
            LOGGER.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(event.getFormattedMessage())
                    .contains("task=notification", "deleted=1500");
        });
    }

    @Test
    void failureInOneTaskDoesNotBlockRemainingTasks() {
        ThrowingTask broken = new ThrowingTask("broken");
        StubTask notification = new StubTask("notification", 0);
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            new DataRetentionService(List.of(broken, notification), CLOCK).cleanup();
        } finally {
            LOGGER.detachAppender(appender);
        }

        assertThat(notification.invoked).isTrue();
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("task=broken");
        });
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        LOGGER.addAppender(appender);
        return appender;
    }

    private static final class StubTask implements RetentionTask {

        private final String name;
        private final Deque<Integer> results = new ArrayDeque<>();
        private final List<Integer> limits = new ArrayList<>();
        private boolean invoked;

        StubTask(String name, int... results) {
            this.name = name;
            for (int result : results) {
                this.results.add(result);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int deleteBatch(Instant now, int limit) {
            invoked = true;
            limits.add(limit);
            return results.removeFirst();
        }
    }

    private record ThrowingTask(String name) implements RetentionTask {

        @Override
        public int deleteBatch(Instant now, int limit) {
            throw new IllegalStateException("boom");
        }
    }
}
