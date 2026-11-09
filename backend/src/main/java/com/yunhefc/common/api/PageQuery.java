package com.yunhefc.common.api;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.Set;

/**
 * 列表分页参数白名单：page 从 1 开始，pageSize 仅允许 10/20/50/100。
 */
public record PageQuery(long page, long pageSize) {

    private static final Set<Long> ALLOWED_SIZES = Set.of(10L, 20L, 50L, 100L);

    public static PageQuery of(long page, long pageSize) {
        if (page < 1 || !ALLOWED_SIZES.contains(pageSize)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页参数不合法");
        }
        return new PageQuery(page, pageSize);
    }
}
