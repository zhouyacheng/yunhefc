package com.yunhefc.common.api;

import com.yunhefc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void acceptsWhitelistedPageSizes() {
        for (long size : new long[]{10, 20, 50, 100}) {
            assertThat(PageQuery.of(1, size).pageSize()).isEqualTo(size);
        }
    }

    @Test
    void rejectsNonWhitelistedPageSize() {
        assertThatThrownBy(() -> PageQuery.of(1, 15))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分页参数不合法");
    }

    @Test
    void rejectsNonPositivePage() {
        assertThatThrownBy(() -> PageQuery.of(0, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分页参数不合法");
    }
}
