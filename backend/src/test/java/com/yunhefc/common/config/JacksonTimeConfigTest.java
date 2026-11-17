package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonTimeConfigTest {

    private static final LocalDateTime EXPECTED = LocalDateTime.of(2026, 11, 12, 20, 0);

    private final ObjectMapper mapper = mapper();

    private static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonTimeConfig().shanghaiLocalDateTime().customize(builder);
        return builder.build();
    }

    @Test
    void serializesWithShanghaiOffset() throws Exception {
        assertThat(mapper.writeValueAsString(EXPECTED)).isEqualTo("\"2026-11-12T20:00:00+08:00\"");
    }

    @Test
    void deserializesUtcOffsetShanghaiOffsetAndLocalTime() throws Exception {
        assertThat(read("2026-11-12T12:00:00Z")).isEqualTo(EXPECTED);
        assertThat(read("2026-11-12T20:00:00+08:00")).isEqualTo(EXPECTED);
        assertThat(read("2026-11-12T20:00:00")).isEqualTo(EXPECTED);
    }

    @Test
    void rejectsBlankString() {
        assertThatThrownBy(() -> read(" "))
                .isInstanceOf(MismatchedInputException.class);
    }

    private LocalDateTime read(String value) throws Exception {
        return mapper.readValue("\"" + value + "\"", LocalDateTime.class);
    }
}
