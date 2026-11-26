package com.yunhefc.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class LedgerPeriodTest {

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    @Test
    void monthUsesCalendarMonthAndPreviousMonth() {
        LedgerPeriod period = LedgerPeriod.of("month", date("2026-11-15"), null, null);

        assertThat(period.period()).isEqualTo("month");
        assertThat(period.from()).isEqualTo(date("2026-11-01"));
        assertThat(period.to()).isEqualTo(date("2026-11-30"));
        assertThat(period.previousFrom()).isEqualTo(date("2026-10-01"));
        assertThat(period.previousTo()).isEqualTo(date("2026-10-31"));
        assertThat(period.trendGranularity()).isEqualTo("day");
    }

    @Test
    void weekStartsOnMonday() {
        // 2026-11-26 为周四。
        LedgerPeriod period = LedgerPeriod.of("week", date("2026-11-26"), null, null);

        assertThat(period.from()).isEqualTo(date("2026-11-23"));
        assertThat(period.to()).isEqualTo(date("2026-11-29"));
        assertThat(period.previousFrom()).isEqualTo(date("2026-11-16"));
        assertThat(period.previousTo()).isEqualTo(date("2026-11-22"));
    }

    @Test
    void yearUsesCalendarYearAndMonthlyTrend() {
        LedgerPeriod period = LedgerPeriod.of("year", date("2026-07-01"), null, null);

        assertThat(period.from()).isEqualTo(date("2026-01-01"));
        assertThat(period.to()).isEqualTo(date("2026-12-31"));
        assertThat(period.previousFrom()).isEqualTo(date("2025-01-01"));
        assertThat(period.previousTo()).isEqualTo(date("2025-12-31"));
        assertThat(period.trendGranularity()).isEqualTo("month");
    }

    @Test
    void customUsesImmediatelyPrecedingWindow() {
        LedgerPeriod period = LedgerPeriod.of("custom", null,
                date("2026-11-01"), date("2026-11-20"));

        assertThat(period.from()).isEqualTo(date("2026-11-01"));
        assertThat(period.to()).isEqualTo(date("2026-11-20"));
        assertThat(period.previousFrom()).isEqualTo(date("2026-10-12"));
        assertThat(period.previousTo()).isEqualTo(date("2026-10-31"));
        assertThat(period.trendGranularity()).isEqualTo("day");
    }

    @Test
    void customLongerThan62DaysUsesMonthlyTrend() {
        LedgerPeriod period = LedgerPeriod.of("custom", null,
                date("2026-01-01"), date("2026-04-30"));

        assertThat(period.trendGranularity()).isEqualTo("month");
        assertThat(period.previousTo()).isEqualTo(date("2025-12-31"));
    }

    @Test
    void normalizesPeriodName() {
        assertThat(LedgerPeriod.normalize(null)).isEqualTo("month");
        assertThat(LedgerPeriod.normalize("  ")).isEqualTo("month");
        assertThat(LedgerPeriod.normalize(" CUSTOM ")).isEqualTo("custom");
        assertThat(LedgerPeriod.isCustom(" custom ")).isTrue();
        assertThat(LedgerPeriod.isCustom("month")).isFalse();
        assertThat(LedgerPeriod.isCustom(null)).isFalse();
    }

    @Test
    void defaultsToCurrentMonth() {
        LedgerPeriod period = LedgerPeriod.of(null, null, null, null);

        assertThat(period.period()).isEqualTo("month");
        assertThat(period.from()).isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .withDayOfMonth(1));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatThrownBy(() -> LedgerPeriod.of("quarter", null, null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LedgerPeriod.of("custom", null, null, date("2026-11-01")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LedgerPeriod.of("custom", null,
                date("2026-11-20"), date("2026-11-01")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LedgerPeriod.of("custom", null,
                date("2010-01-01"), date("2026-11-01")))
                .isInstanceOf(BusinessException.class);
    }
}
