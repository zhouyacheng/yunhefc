package com.yunhefc.infra.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.messaging.AuditEventSink;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.messaging.NotificationEventSink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

class OutboxEventDispatcherTest {

    private final AuditEventSink auditEventSink = mock(AuditEventSink.class);
    private final NotificationEventSink notificationEventSink = mock(NotificationEventSink.class);
    private final OutboxEventDispatcher dispatcher = new OutboxEventDispatcher(
            auditEventSink, notificationEventSink, new ObjectMapper());

    @Test
    void auditEventRoutesToAuditHandlerWithOutboxPosition() {
        OutboxEvent event = event(42L, EventTypes.AUDIT_RECORDED, "{\"action\":\"UPDATE\"}");

        dispatcher.dispatch(event).join();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(auditEventSink).apply(message.capture(), eq("outbox.audit"), eq(0), eq(42L));
        verifyNoInteractions(notificationEventSink);
        assertThat(message.getValue())
                .contains(event.getEventId())
                .contains("\"payload\":\"{\\\"action\\\":\\\"UPDATE\\\"}\"");
    }

    @Test
    void notificationEventRoutesToNotificationHandlerWithOutboxPosition() {
        OutboxEvent event = event(7L, EventTypes.ACCOUNT_APPROVED, "{\"userId\":9}");

        dispatcher.dispatch(event).join();

        verify(notificationEventSink).apply(anyString(), eq("outbox.notification"), eq(0), eq(7L));
        verifyNoInteractions(auditEventSink);
    }

    @Test
    void unknownEventTypeIsRejected() {
        OutboxEvent event = event(1L, "UNKNOWN_EVENT", "{}");

        assertThatThrownBy(() -> dispatcher.dispatch(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_EVENT");
    }

    private OutboxEvent event(Long id, String eventType, String payloadJson) {
        OutboxEvent event = new OutboxEvent();
        event.setId(id);
        event.setEventId(UUID.randomUUID().toString());
        event.setTeamId(1L);
        event.setAggregateType("MATCH");
        event.setAggregateId(77L);
        event.setEventType(eventType);
        event.setPayloadJson(payloadJson);
        return event;
    }
}
