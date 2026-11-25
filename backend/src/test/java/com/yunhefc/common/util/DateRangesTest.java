package com.yunhefc.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.time.ShanghaiTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

class DateRangesTest {

    @Test
    void returnsNullWhenBothDatesAreBlank() {
        assertThat(DateRanges.parse(null, null)).isNull();
        assertThat(DateRanges.parse("  ", "")).isNull();
    }

    @Test
    void rejectsOneSidedRange() {
        assertThatThrownBy(() -> DateRanges.parse("2099-06-01", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须同时提供");
        assertThatThrownBy(() -> DateRanges.parse(null, "2099-06-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须同时提供");
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> DateRanges.parse("2099-06-15", "2099-06-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能晚于");
    }

    @Test
    void rejectsMalformedOrImpossibleDates() {
        assertThatThrownBy(() -> DateRanges.parse("2099-6-1", "2099-06-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> DateRanges.parse("2099-02-30", "2099-03-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> DateRanges.parse("2099-13-01", "2099-13-02"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM-DD");
    }

    @Test
    void rejectsDatesOutsideSupportedWindow() {
        assertThatThrownBy(() -> DateRanges.parse("1999-12-31", "2000-01-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2000-01-01");
        assertThatThrownBy(() -> DateRanges.parse("2100-12-31", "2101-01-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2100-12-31");
    }

    @Test
    void convertsShanghaiNaturalDaysToHalfOpenInstants() {
        DateRanges.Range range = DateRanges.parse("2099-06-01", "2099-06-15");

        assertThat(range.start()).isEqualTo(LocalDate.of(2099, 6, 1));
        assertThat(range.end()).isEqualTo(LocalDate.of(2099, 6, 15));
        assertThat(range.startAt()).isEqualTo(ShanghaiTime.parseLenient("2099-05-31T16:00:00Z"));
        assertThat(range.endExclusiveAt()).isEqualTo(ShanghaiTime.parseLenient("2099-06-15T16:00:00Z"));
    }

    @Test
    void allowsSingleDayRanges() {
        DateRanges.Range range = DateRanges.parse("2099-06-01", "2099-06-01");

        assertThat(range.startAt()).isEqualTo(ShanghaiTime.parseLenient("2099-05-31T16:00:00Z"));
        assertThat(range.endExclusiveAt()).isEqualTo(ShanghaiTime.parseLenient("2099-06-01T16:00:00Z"));
    }

    @Test
    void parsesBoundedMonths() {
        assertThat(DateRanges.parseMonth(null)).isNull();
        assertThat(DateRanges.parseMonth("  ")).isNull();
        assertThat(DateRanges.parseMonth("2099-06")).isEqualTo(YearMonth.of(2099, 6));

        assertThatThrownBy(() -> DateRanges.parseMonth("2099-6"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM");
        assertThatThrownBy(() -> DateRanges.parseMonth("2099-13"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("YYYY-MM");
        assertThatThrownBy(() -> DateRanges.parseMonth("1999-12"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2000-01");
        assertThatThrownBy(() -> DateRanges.parseMonth("2101-01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2100-12");
    }
}
