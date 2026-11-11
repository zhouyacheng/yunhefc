package com.yunhefc.infra.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.config.MessagingProperties;
import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.contracts.messaging.EventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

class OutboxRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void routesAccountApprovedEventToNotificationTopic() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setEventId(UUID.randomUUID().toString());
        event.setTeamId(1L);
        event.setAggregateType("USER");
        event.setAggregateId(9L);
        event.setEventType(EventTypes.ACCOUNT_APPROVED);
        event.setPayloadJson("{\"userId\":9}");
        when(mapper.findClaimable(20)).thenReturn(List.of(event));
        when(mapper.claim(eq(event.getId()), anyString(), any(Instant.class))).thenReturn(1);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        OutboxRelay relay = new OutboxRelay(mapper, kafkaTemplate, new ObjectMapper(),
                new MessagingProperties(), new OutboxProperties(), Clock.systemUTC());
        relay.relay();

        verify(kafkaTemplate).send(eq("yunhefc.notification.events"), eq("9"), anyString());
        verify(mapper).markSent(event.getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void routesRegistrationEventsToNotificationTopicByMatchKey() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        OutboxEvent confirmed = new OutboxEvent();
        confirmed.setId(1L);
        confirmed.setEventId(UUID.randomUUID().toString());
        confirmed.setTeamId(1L);
        confirmed.setAggregateType("MATCH");
        confirmed.setAggregateId(77L);
        confirmed.setEventType(EventTypes.REGISTRATION_CONFIRMED);
        confirmed.setPayloadJson("{\"userId\":9,\"matchId\":77}");
        OutboxEvent promoted = new OutboxEvent();
        promoted.setId(2L);
        promoted.setEventId(UUID.randomUUID().toString());
        promoted.setTeamId(1L);
        promoted.setAggregateType("MATCH");
        promoted.setAggregateId(77L);
        promoted.setEventType(EventTypes.WAITLIST_PROMOTED);
        promoted.setPayloadJson("{\"userId\":10,\"matchId\":77}");
        when(mapper.findClaimable(20)).thenReturn(List.of(confirmed, promoted));
        when(mapper.claim(eq(1L), anyString(), any(Instant.class))).thenReturn(1);
        when(mapper.claim(eq(2L), anyString(), any(Instant.class))).thenReturn(1);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));

        OutboxRelay relay = new OutboxRelay(mapper, kafkaTemplate, new ObjectMapper(),
                new MessagingProperties(), new OutboxProperties(), Clock.systemUTC());
        relay.relay();

        verify(kafkaTemplate).send(eq("yunhefc.notification.events"), eq("77"),
                org.mockito.ArgumentMatchers.contains("REGISTRATION_CONFIRMED"));
        verify(kafkaTemplate).send(eq("yunhefc.notification.events"), eq("77"),
                org.mockito.ArgumentMatchers.contains("WAITLIST_PROMOTED"));
        verify(mapper).markSent(confirmed.getId());
        verify(mapper).markSent(promoted.getId());
    }
}
