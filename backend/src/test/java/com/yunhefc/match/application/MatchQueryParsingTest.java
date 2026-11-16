package com.yunhefc.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

class MatchQueryParsingTest {

    @Test
    void returnsNullWhenBothDatesAreBlank() {
        assertThat(MatchQueryParsing.dateRange(null, null)).isNull();
        assertThat(MatchQueryParsing.dateRange("  ", "")).isNull();
    }

    @Test
    void rejectsOneSidedRange() {
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("2099-06-01", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须同时提供");
        assertThatThrownBy(() -> MatchQueryParsing.dateRange(null, "2099-06-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须同时提供");
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("2099-06-15", "2099-06-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能晚于");
    }

    @Test
    void rejectsMalformedOrImpossibleDates() {
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("2099-6-1", "2099-06-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("2099-02-30", "2099-03-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("2099-13-01", "2099-13-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void rejectsDatesOutsideSupportedWindow() {
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("1999-12-31", "2000-01-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2000-01-01");
        assertThatThrownBy(() -> MatchQueryParsing.dateRange("2100-12-31", "2101-01-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2100-12-31");
    }

    @Test
    void convertsShanghaiNaturalDaysToHalfOpenInstants() {
        MatchQueryParsing.DateRange range =
                MatchQueryParsing.dateRange("2099-06-01", "2099-06-15");

        assertThat(range.start()).isEqualTo(LocalDate.of(2099, 6, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2099, 6, 15));
        assertThat(range.startInstant()).isEqualTo(Instant.parse("2099-05-31T16:00:00Z"));
        assertThat(range.endExclusiveInstant()).isEqualTo(Instant.parse("2099-06-15T16:00:00Z"));
    }

    @Test
    void allowsSingleDayRanges() {
        MatchQueryParsing.DateRange range =
                MatchQueryParsing.dateRange("2099-06-01", "2099-06-01");

        assertThat(range.startInstant()).isEqualTo(Instant.parse("2099-05-31T16:00:00Z"));
        assertThat(range.endExclusiveInstant()).isEqualTo(Instant.parse("2099-06-01T16:00:00Z"));
    }
}
