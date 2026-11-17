package com.yunhefc.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.yunhefc.common.time.ShanghaiTime;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 全局 LocalDateTime 按 Asia/Shanghai +08:00 输出；请求兼容 Z、偏移与本地时间。
 * 同时服务 Web、outbox 载荷、审计快照/导出、统计缓存与幂等回放。
 */
@Configuration
public class JacksonTimeConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer shanghaiLocalDateTime() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer())
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer());
    }

    private static final class LocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(ShanghaiTime.formatIso(value));
        }
    }

    private static final class LocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            String value = parser.getText();
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return ShanghaiTime.parseLenient(value);
            } catch (RuntimeException ex) {
                throw context.weirdStringException(value, LocalDateTime.class,
                        "expected ISO-8601 datetime");
            }
        }
    }
}
