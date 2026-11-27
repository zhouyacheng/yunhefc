package com.yunhefc.infra.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.contracts.messaging.EventTypes;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class OutboxRelayTest {

    private final OutboxEventMapper mapper = mock(OutboxEventMapper.class);
    private final OutboxEventDispatcher dispatcher = mock(OutboxEventDispatcher.class);
    private final OutboxProperties properties = new OutboxProperties();
    private final OutboxRelay relay = new OutboxRelay(mapper, dispatcher, properties);

    @Test
    void marksSentWhenDispatchCompletes() {
        OutboxEvent event = event(1L, EventTypes.ACCOUNT_APPROVED);
        when(mapper.findClaimable(20)).thenReturn(List.of(event));
        when(mapper.claim(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(dispatcher.dispatch(event)).thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        verify(dispatcher).dispatch(event);
        verify(mapper).markSent(1L);
        verify(mapper, never()).markFailed(any(), any(), any(), any(Integer.class));
    }

    @Test
    void marksFailedWhenDispatchFails() {
        OutboxEvent event = event(2L, EventTypes.AUDIT_RECORDED);
        when(mapper.findClaimable(20)).thenReturn(List.of(event));
        when(mapper.claim(eq(2L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(dispatcher.dispatch(event))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        relay.relay();

        verify(mapper).markFailed(eq(2L), any(LocalDateTime.class), contains("boom"), eq(5));
        verify(mapper, never()).markSent(any());
    }

    @Test
    void marksFailedWhenDispatchTimesOut() {
        properties.setDispatchTimeoutMs(50);
        OutboxEvent event = event(3L, EventTypes.ACCOUNT_APPROVED);
        when(mapper.findClaimable(20)).thenReturn(List.of(event));
        when(mapper.claim(eq(3L), anyString(), any(LocalDateTime.class))).thenReturn(1);
        when(dispatcher.dispatch(event)).thenReturn(new CompletableFuture<>());

        relay.relay();

        verify(mapper).markFailed(eq(3L), any(LocalDateTime.class), contains("投递超时"), eq(5));
        verify(mapper, never()).markSent(any());
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.setEnabled(false);

        relay.relay();

        verifyNoInteractions(mapper, dispatcher);
    }

    @Test
    void skipsEventsClaimedByAnotherRelay() {
        OutboxEvent event = event(4L, EventTypes.ACCOUNT_APPROVED);
        when(mapper.findClaimable(20)).thenReturn(List.of(event));
        when(mapper.claim(eq(4L), anyString(), any(LocalDateTime.class))).thenReturn(0);

        relay.relay();

        verifyNoInteractions(dispatcher);
        verify(mapper, never()).markSent(any());
    }

    private OutboxEvent event(Long id, String eventType) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setEventId(UUID.randomUUID().toString());
        event.setTeamId(1L);
        event.setAggregateType("MATCH");
        event.setAggregateId(77L);
        event.setEventType(eventType);
        event.setPayloadJson("{}");
        return event;
    }
}
