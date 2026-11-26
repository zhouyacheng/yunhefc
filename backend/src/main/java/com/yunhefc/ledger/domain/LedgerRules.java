package com.yunhefc.ledger.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 账本字段与分类方向规则；数据库禁用 CHECK，全部由应用层强校验（04 §8.1）。
 */
public final class LedgerRules {

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private LedgerRules() {
    }

    public static LedgerType type(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return LedgerType.of(code);
        } catch (IllegalArgumentException ex) {
            throw invalid("type", "收支方向不合法");
        }
    }

    public static BigDecimal amount(BigDecimal amount) {
        if (amount == null) {
            throw invalid("amount", "金额不能为空");
        }
        if (amount.signum() <= 0) {
            throw invalid("amount", "金额必须大于 0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw invalid("amount", "金额不能超过 99999999.99");
        }
        if (amount.scale() > 2) {
            throw invalid("amount", "金额最多两位小数");
        }
        // 响应固定两位小数，避免创建/编辑回显 300.0 而列表返回 300.00。
        return amount.setScale(2);
    }

    /** 摘要非必填：null/空白归一为空字符串（列 NOT NULL），超 255 字符拒绝。 */
    public static String descriptionOrEmpty(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "";
        }
        if (normalized.length() > 255) {
            throw invalid("description", "摘要不能超过 255 个字符");
        }
        return normalized;
    }

    public static String categoryName(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.length() > 32) {
            throw invalid("name", "分类名称需为 1–32 个字符");
        }
        return normalized;
    }

    public static LedgerType categoryDirection(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return LedgerType.of(code);
        } catch (IllegalArgumentException ex) {
            throw invalid("direction", "分类方向不合法");
        }
    }

    /** 通用分类（direction=NULL）可用于收支两侧。 */
    public static void requireDirectionMatch(LedgerType type, LedgerCategory category) {
        if (category.getDirection() != null && category.getDirection() != type) {
            throw invalid("categoryId", "流水方向与分类方向不一致");
        }
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }

    public static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(), Map.of("details", Map.of(field, message)));
    }
}
