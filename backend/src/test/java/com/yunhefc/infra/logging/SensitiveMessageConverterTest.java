package com.yunhefc.infra.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

class SensitiveMessageConverterTest {

    @Test
    void masksSensitiveValuesInRenderedMessage() {
        LoggerContext context = new LoggerContext();
        context.start();
        SensitiveMessageConverter converter = new SensitiveMessageConverter();
        converter.setContext(context);
        converter.start();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerContext(context);
        event.setLevel(Level.INFO);
        event.setLoggerName("com.yunhefc.LogTest");
        event.setMessage("user login, phone={}, email={}");
        event.setArgumentArray(new Object[]{"13812345678", "zhangsan@example.com"});

        assertThat(converter.convert(event))
                .isEqualTo("user login, phone=138****5678, email=***@example.com");

        converter.stop();
        context.stop();
    }
}
