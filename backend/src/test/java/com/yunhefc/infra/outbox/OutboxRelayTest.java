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
}
