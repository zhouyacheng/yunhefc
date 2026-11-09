package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.concurrent.CompletableFuture;

class KafkaConfigTest {

    @Test
    @SuppressWarnings("unchecked")
    void retriesAreBoundedAndExhaustedRecordsGoToDeadLetterTopic() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        MessagingProperties properties = new MessagingProperties();
        properties.getRetry().setMaxRetries(2);
        properties.getRetry().setIntervalMs(0);
        DefaultErrorHandler handler = new KafkaConfig().kafkaErrorHandler(kafkaTemplate, properties);

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("yunhefc.audit.events", 0, 7L, "key", "broken");
        Consumer<Object, Object> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        RuntimeException failure = new RuntimeException("broken message");

        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();

        ArgumentCaptor<ProducerRecord<String, String>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("yunhefc.audit.events-dlt");
        assertThat(sent.getValue().value()).isEqualTo("broken");
    }
}
