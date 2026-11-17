package com.yunhefc.audit.application;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventHandler handler;

    @KafkaListener(topics = "${yunhefc.kafka.audit-topic:yunhefc.audit.events}",
            groupId = AuditEventHandler.GROUP,
            autoStartup = "${yunhefc.kafka.listener-enabled:true}")
    public void consume(String message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment acknowledgment) {
        log.info("audit event received, topic={}, partition={}, offset={}, messageLength={}",
                topic, partition, offset, message.length());
        handler.apply(message, topic, partition, offset);
        acknowledgment.acknowledge();
    }

    public void apply(String message, String topic, int partition, long offset) {
        handler.apply(message, topic, partition, offset);
    }
}
