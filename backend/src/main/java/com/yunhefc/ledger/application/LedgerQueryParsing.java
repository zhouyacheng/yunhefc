package com.yunhefc.ledger.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.ledger.domain.LedgerRules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 账本列表、汇总与统计的公共参数解析。
 */
final class LedgerQueryParsing {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);

    private LedgerQueryParsing() {
    }

    static LocalDateTime time(String value, String field) {
        String normalized = LedgerRules.normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return ShanghaiTime.parseLenient(normalized);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "时间必须为 ISO-8601 格式",
                    Map.of("details", Map.of(field, "时间必须为 ISO-8601 格式")));
        }
    }

    static void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "开始时间不能晚于结束时间",
                    Map.of("details", Map.of("from", "开始时间不能晚于结束时间")));
        }
    }

    /** LED-14 的自然日参数；空值返回 null，格式非法或超出 2000-01-01 至 2100-12-31 返回 40001。 */
    static LocalDate date(String value, String field) {
        String normalized = LedgerRules.normalize(value);
        if (normalized == null) {
            return null;
        }
        LocalDate date;
        try {
            date = DATE_PATTERN.matcher(normalized).matches() ? LocalDate.parse(normalized) : null;
        } catch (DateTimeParseException ex) {
            date = null;
        }
        if (date == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "日期必须为 YYYY-MM-DD",
                    Map.of("details", Map.of(field, "日期必须为 YYYY-MM-DD")));
        }
        if (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "日期需在 2000-01-01 至 2100-12-31",
                    Map.of("details", Map.of(field, "日期需在 2000-01-01 至 2100-12-31")));
        }
        return date;
    }

    static String keyword(String value) {
        String keyword = LedgerRules.normalize(value);
        if (keyword != null && keyword.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "关键字长度不能超过 64",
                    Map.of("details", Map.of("keyword", "关键字长度不能超过 64")));
        }
        return keyword;
    }

    static Long positiveId(Long id, String field) {
        if (id != null && id < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    ErrorCode.VALIDATION_FAILED.message(),
                    Map.of("details", Map.of(field, "ID 必须大于等于 1")));
        }
        return id;
    }

}
