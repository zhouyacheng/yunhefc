package com.yunhefc.content.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yunhefc.infra.storage.ObjectRemoval;
import com.yunhefc.infra.storage.OssGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

class ContentObjectDeletionTest {

    private static final Logger LOGGER = (Logger) LoggerFactory.getLogger(ObjectRemoval.class);

    private final OssGateway ossGateway = mock(OssGateway.class);
    private final ContentObjectDeletion deletion =
            new ContentObjectDeletion(new ObjectRemoval(ossGateway));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deletesEveryObjectImmediatelyWithoutTransaction() {
        deletion.deleteAll(List.of("content/1/a.jpg", "content/1/b.png"));

        verify(ossGateway).deleteObject("content/1/a.jpg");
        verify(ossGateway).deleteObject("content/1/b.png");
    }

    @Test
    void ossFailureIsSwallowedAndRemainingObjectsStillDeleted() {
        doThrow(new IllegalStateException("oss down")).when(ossGateway).deleteObject("content/1/a.jpg");
        ListAppender<ILoggingEvent> appender = attachAppender();

        try {
            assertThatCode(() -> deletion.deleteAll(List.of("content/1/a.jpg", "content/1/b.png")))
                    .doesNotThrowAnyException();
        } finally {
            LOGGER.detachAppender(appender);
        }

        verify(ossGateway).deleteObject("content/1/b.png");
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("content/1/a.jpg");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        LOGGER.addAppender(appender);
        return appender;
    }

    @Test
    void skipsBlankAndNullKeys() {
        deletion.deleteAll(Arrays.asList(null, "", "   "));

        verifyNoInteractions(ossGateway);
    }

    @Test
    void emptyCollectionDoesNothing() {
        deletion.deleteAll(List.of());

        verifyNoInteractions(ossGateway);
    }

    @Test
    void nullCollectionDoesNothing() {
        deletion.deleteAll(null);

        verifyNoInteractions(ossGateway);
    }

    @Test
    void defersDeletionUntilTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        deletion.deleteAll(List.of("content/1/a.jpg"));
        verify(ossGateway, never()).deleteObject("content/1/a.jpg");

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        verify(ossGateway).deleteObject("content/1/a.jpg");
    }
}
