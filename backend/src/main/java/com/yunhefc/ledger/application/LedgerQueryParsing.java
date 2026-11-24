package com.yunhefc.ledger.application;

import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.ledger.domain.LedgerRules;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 账本列表与汇总的公共筛选参数解析。
 */
final class LedgerQueryParsing {

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

    static PageQuery page(long page, long pageSize) {
        return PageQuery.of(page, pageSize);
    }
}
