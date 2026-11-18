package com.yunhefc.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ShanghaiTimeTest {

    private static final LocalDateTime EXPECTED = LocalDateTime.of(2026, 11, 12, 20, 0);

    @Test
    void formatsWithShanghaiOffset() {
        assertThat(ShanghaiTime.formatIso(EXPECTED)).isEqualTo("2026-11-12T20:00:00+08:00");
    }

    @Test
    void parsesUtcOffsetShanghaiOffsetAndLocalTime() {
        assertThat(ShanghaiTime.parseLenient("2026-11-12T12:00:00Z")).isEqualTo(EXPECTED);
        assertThat(ShanghaiTime.parseLenient("2026-11-12T20:00:00+08:00")).isEqualTo(EXPECTED);
        assertThat(ShanghaiTime.parseLenient("2026-11-12T20:00:00")).isEqualTo(EXPECTED);
    }
}
