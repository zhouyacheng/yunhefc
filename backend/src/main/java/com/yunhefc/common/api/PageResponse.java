package com.yunhefc.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public record PageResponse<T>(List<T> items, long total, long page, long pageSize, long totalPages) {

    public static <T> PageResponse<T> of(IPage<T> result) {
        return new PageResponse<>(
                result.getRecords(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getPages());
    }

    public static <T> PageResponse<T> of(List<T> items, long total, long page, long pageSize) {
        long totalPages = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResponse<>(items, total, page, pageSize, totalPages);
    }
}
