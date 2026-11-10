package com.yunhefc.match.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchType;

/**
 * 比赛列表（MAT-02/09）公共的筛选参数解析。
 */
final class MatchQueryParsing {

    static final String SCOPE_ALL = "all";
    static final String SCOPE_UPCOMING = "upcoming";
    static final String SCOPE_HISTORY = "history";
    static final String SORT_START_AT_ASC = "startAt:asc";
    static final String SORT_START_AT_DESC = "startAt:desc";

    private MatchQueryParsing() {
    }

    static MatchType type(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return MatchType.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛类型不合法");
        }
    }

    static MatchStatus status(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return MatchStatus.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛状态不合法");
        }
    }

    static String seasonFilter(String requested) {
        String season = normalize(requested);
        return season == null ? null : validSeason(season);
    }

    static String scope(String requested) {
        String scope = normalize(requested);
        if (scope == null) {
            return SCOPE_ALL;
        }
        if (!scope.equals(SCOPE_ALL) && !scope.equals(SCOPE_UPCOMING) && !scope.equals(SCOPE_HISTORY)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "scope 参数不合法");
        }
        return scope;
    }

    static boolean ascending(String requested) {
        String sort = normalize(requested);
        if (sort == null || sort.equals(SORT_START_AT_DESC)) {
            return false;
        }
        if (sort.equals(SORT_START_AT_ASC)) {
            return true;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数不合法");
    }

    static String validSeason(String season) {
        if (!season.matches("\\d{4}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "赛季必须为四位年份");
        }
        return season;
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }
}
