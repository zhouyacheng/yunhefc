package com.yunhefc.common.util;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 列表共用的时间筛选参数：月份与自定义日期范围，均按 Asia/Shanghai 自然月/自然日。
 */
public final class DateRanges {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);
    private static final YearMonth MIN_MONTH = YearMonth.from(MIN_DATE);
    private static final YearMonth MAX_MONTH = YearMonth.from(MAX_DATE);
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");

    private DateRanges() {
    }

    /**
     * 空值不筛选；格式非法或超出 2000-01 至 2100-12 返回 40001。
     */
    public static YearMonth parseMonth(String value) {
        String month = normalize(value);
        if (month == null) {
            return null;
        }
        YearMonth parsed;
        try {
            parsed = YearMonth.parse(month, MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "月份必须为 YYYY-MM");
        }
        if (parsed.isBefore(MIN_MONTH) || parsed.isAfter(MAX_MONTH)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "月份需在 2000-01 至 2100-12");
        }
        return parsed;
    }

    /**
     * 两端都为空时不筛选；只提供一端、格式非法或起止颠倒返回 40001。
     */
    public static Range parse(String start, String end) {
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
        return new Range(from, to);
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

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }

    public record Range(LocalDate start, LocalDate end) {

        public LocalDateTime startAt() {
            return start.atStartOfDay();
        }

        public LocalDateTime endExclusiveAt() {
            return end.plusDays(1).atStartOfDay();
        }
    }
}
