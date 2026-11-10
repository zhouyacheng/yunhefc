package com.yunhefc.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void append(Long teamId, String aggregateType, Long aggregateId, String eventType, Object payload) {
        OutboxEvent event = newEvent(teamId, aggregateType, aggregateId, eventType, payload);
        outboxEventMapper.insert(event);
    }

    public void appendBatch(List<OutboxEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        outboxEventMapper.insertBatch(events);
    }

    public OutboxEvent newEvent(Long teamId, String aggregateType, Long aggregateId,
                                String eventType, Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTeamId(teamId);
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayloadJson(toJson(payload));
        event.setStatus(0);
        event.setAttempts(0);
        event.setCreatedAt(Instant.now(clock));
        return event;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化 Outbox 事件", ex);
        }
    }
}
