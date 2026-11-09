package com.yunhefc.infra.outbox;

public record OutboxMessage(
        String eventId,
        Long teamId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload) {
}
