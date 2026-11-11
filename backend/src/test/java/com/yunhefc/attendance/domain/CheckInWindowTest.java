package com.yunhefc.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

class CheckInWindowTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void opensFourHoursBeforeStartAndClosesAtEndOfMatchDay() {
        Instant start = at("2026-11-15T20:00:00");

        assertThat(CheckInWindow.opensAt(start)).isEqualTo(at("2026-11-15T16:00:00"));
        assertThat(CheckInWindow.closesAt(start)).isEqualTo(at("2026-11-16T00:00:00"));
        assertThat(CheckInWindow.isOpen(start, at("2026-11-15T15:59:59"))).isFalse();
        assertThat(CheckInWindow.isOpen(start, at("2026-11-15T16:00:00"))).isTrue();
        assertThat(CheckInWindow.isOpen(start, at("2026-11-15T23:59:59"))).isTrue();
        assertThat(CheckInWindow.isOpen(start, at("2026-11-16T00:00:00"))).isFalse();
    }

    @Test
    void earlyMorningMatchOpensOnPreviousDay() {
        Instant start = at("2026-11-16T01:00:00");

        assertThat(CheckInWindow.opensAt(start)).isEqualTo(at("2026-11-15T21:00:00"));
        assertThat(CheckInWindow.isOpen(start, at("2026-11-15T23:00:00"))).isTrue();
    }

    private static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SHANGHAI).toInstant();
    }
}
