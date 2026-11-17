package com.yunhefc.match.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchTimeRules;
import com.yunhefc.match.domain.MatchType;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 比赛列表（MAT-02/09）公共的筛选参数解析。
 */
final class MatchQueryParsing {

    static final String SCOPE_ALL = "all";
    static final String SCOPE_UPCOMING = "upcoming";
    static final String SCOPE_HISTORY = "history";
    static final String SORT_START_AT_ASC = "startAt:asc";
    static final String SORT_START_AT_DESC = "startAt:desc";

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

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

    /**
     * 管理端列表排序解析：缺省按开始时间倒序，仅接受四个白名单字段。
     */
    static AdminSort adminSort(String requested) {
        String sort = normalize(requested);
        if (sort == null) {
            return new AdminSort(AdminSortField.START_AT, false);
        }
        int separator = sort.indexOf(':');
        if (separator <= 0 || separator == sort.length() - 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数不合法");
        }
        String field = sort.substring(0, separator);
        boolean ascending = switch (sort.substring(separator + 1)) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数不合法");
        };
        AdminSortField sortField = switch (field) {
            case "startAt" -> AdminSortField.START_AT;
            case "endAt" -> AdminSortField.END_AT;
            case "season" -> AdminSortField.SEASON;
            case "type" -> AdminSortField.TYPE;
            case "seasonMatchNo" -> AdminSortField.SEASON_MATCH_NO;
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数不合法");
        };
        return new AdminSort(sortField, ascending);
    }

    static String validSeason(String season) {
        if (!season.matches("\\d{4}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "赛季必须为四位年份");
        }
        return season;
    }

    /**
     * 解析自定义日期范围；按 Asia/Shanghai 自然日左闭右开。
     */
    static DateRange dateRange(String start, String end) {
        String startDate = normalize(start);
        String endDate = normalize(end);
        if (startDate == null && endDate == null) {
            return null;
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "开始日期与结束日期必须同时提供");
        }
        LocalDate from = parseDate(startDate);
        LocalDate to = parseDate(endDate);
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "开始日期不能晚于结束日期");
        }
        return new DateRange(from, to);
    }

    private static LocalDate parseDate(String value) {
        if (!DATE_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "日期必须为 YYYY-MM-DD");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "日期必须为 YYYY-MM-DD");
        }
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "日期范围需在 2000-01-01 至 2100-12-31");
        }
        return date;
    }

    enum AdminSortField {
        START_AT,
        END_AT,
        SEASON,
        TYPE,
        SEASON_MATCH_NO
    }

    record AdminSort(AdminSortField field, boolean ascending) {
    }

    record DateRange(LocalDate start, LocalDate end) {

        LocalDateTime startAt() {
            return start.atStartOfDay();
        }

        LocalDateTime endExclusiveAt() {
            return end.plusDays(1).atStartOfDay();
        }
    }

    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }
}
