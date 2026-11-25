package com.yunhefc.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

class MatchQueryParsingTest {

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
