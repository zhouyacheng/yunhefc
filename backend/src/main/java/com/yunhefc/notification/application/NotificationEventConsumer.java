package com.yunhefc.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationEventHandler handler;

    @KafkaListener(topics = "${yunhefc.kafka.notification-topic:yunhefc.notification.events}",
            groupId = NotificationEventHandler.GROUP,
            autoStartup = "${yunhefc.kafka.listener-enabled:true}")
    public void consume(String message,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment acknowledgment) {
        handler.apply(message, topic, partition, offset);
        acknowledgment.acknowledge();
    }

    public void apply(String message, String topic, int partition, long offset) {
        handler.apply(message, topic, partition, offset);
    }
}
