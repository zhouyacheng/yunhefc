package com.yunhefc.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
        assertThat(range.startAt()).isEqualTo(ShanghaiTime.parseLenient("2099-05-31T16:00:00Z"));
        assertThat(range.endExclusiveAt()).isEqualTo(ShanghaiTime.parseLenient("2099-06-15T16:00:00Z"));
    }

    @Test
    void allowsSingleDayRanges() {
        MatchQueryParsing.DateRange range =
                MatchQueryParsing.dateRange("2099-06-01", "2099-06-01");

        assertThat(range.startAt()).isEqualTo(ShanghaiTime.parseLenient("2099-05-31T16:00:00Z"));
        assertThat(range.endExclusiveAt()).isEqualTo(ShanghaiTime.parseLenient("2099-06-01T16:00:00Z"));
    }

    @Test
    void defaultsAdminSortToStartAtDescending() {
        MatchQueryParsing.AdminSort sort = MatchQueryParsing.adminSort(null);

        assertThat(sort.field()).isEqualTo(MatchQueryParsing.AdminSortField.START_AT);
        assertThat(sort.ascending()).isFalse();
        assertThat(MatchQueryParsing.adminSort("  ").field())
                .isEqualTo(MatchQueryParsing.AdminSortField.START_AT);
    }

    @Test
    void parsesWhitelistedAdminSortFields() {
        assertThat(MatchQueryParsing.adminSort("startAt:asc").field())
                .isEqualTo(MatchQueryParsing.AdminSortField.START_AT);
        assertThat(MatchQueryParsing.adminSort("endAt:desc").field())
                .isEqualTo(MatchQueryParsing.AdminSortField.END_AT);
        assertThat(MatchQueryParsing.adminSort("season:asc").field())
                .isEqualTo(MatchQueryParsing.AdminSortField.SEASON);
        assertThat(MatchQueryParsing.adminSort("type:desc").field())
                .isEqualTo(MatchQueryParsing.AdminSortField.TYPE);
        assertThat(MatchQueryParsing.adminSort("seasonMatchNo:asc").field())
                .isEqualTo(MatchQueryParsing.AdminSortField.SEASON_MATCH_NO);
        assertThat(MatchQueryParsing.adminSort("seasonMatchNo:desc").ascending()).isFalse();
        assertThat(MatchQueryParsing.adminSort("type:asc").ascending()).isTrue();
        assertThat(MatchQueryParsing.adminSort("type:desc").ascending()).isFalse();
    }

    @Test
    void rejectsInvalidAdminSort() {
        assertThatThrownBy(() -> MatchQueryParsing.adminSort("name:asc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排序参数不合法");
        assertThatThrownBy(() -> MatchQueryParsing.adminSort("startAt:sideways"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排序参数不合法");
        assertThatThrownBy(() -> MatchQueryParsing.adminSort("startAt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排序参数不合法");
        assertThatThrownBy(() -> MatchQueryParsing.adminSort(":asc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排序参数不合法");
    }
}
