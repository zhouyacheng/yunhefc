package com.yunhefc.activity.application;

import com.yunhefc.activity.domain.ActivityStatus;
import com.yunhefc.activity.domain.ActivityType;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 活动列表（ACT-01/06）公共的筛选参数解析。
 */
final class ActivityQueryParsing {

    static final String SCOPE_ALL = "all";
    static final String SCOPE_LATEST = "latest";
    static final String SCOPE_HISTORY = "history";
    private static final String SORT_START_AT_ASC = "startAt:asc";
    private static final String SORT_START_AT_DESC = "startAt:desc";
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private ActivityQueryParsing() {
    }

    static ActivityType type(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return ActivityType.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动类型不合法");
        }
    }

    static ActivityStatus status(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return ActivityStatus.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动状态不合法");
        }
    }

    static String scope(String requested) {
        String scope = normalize(requested);
        if (scope == null) {
            return SCOPE_ALL;
        }
        if (!scope.equals(SCOPE_ALL) && !scope.equals(SCOPE_LATEST) && !scope.equals(SCOPE_HISTORY)) {
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

    static String keyword(String requested) {
        String keyword = normalize(requested);
        if (keyword != null && keyword.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "关键字长度不能超过 64");
        }
        return keyword;
    }

    /**
     * 管理端活动列表的自定义日期范围；按 Asia/Shanghai 自然日左闭右开过滤 start_at。
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

    static void validatePaging(long page, long pageSize) {
        PageQuery.of(page, pageSize);
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
