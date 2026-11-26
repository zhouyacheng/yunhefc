package com.yunhefc.content.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

class ContentRulesTest {

    @Test
    void titleIsTrimmedAndBounded() {
        assertThat(ContentRules.requireTitle("  队内合影  ")).isEqualTo("队内合影");
        assertThat(ContentRules.requireTitle("a".repeat(64))).isEqualTo("a".repeat(64));
        assertInvalidTitle(null, "相册名称不能为空");
        assertInvalidTitle("   ", "相册名称不能为空");
        assertInvalidTitle("a".repeat(65), "相册名称不能超过 64 个字符");
    }

    @Test
    void ownerRejectsMatchAndActivityTogether() {
        assertThatCode(() -> ContentRules.requireAtMostOneOwner(null, null)).doesNotThrowAnyException();
        assertThatCode(() -> ContentRules.requireAtMostOneOwner(1L, null)).doesNotThrowAnyException();
        assertThatCode(() -> ContentRules.requireAtMostOneOwner(null, 2L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ContentRules.requireAtMostOneOwner(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void photoSlotAllows200AndRejects201() {
        assertThatCode(() -> ContentRules.requirePhotoSlot(199)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ContentRules.requirePhotoSlot(200))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONTENT_ALBUM_FULL));
    }

    private void assertInvalidTitle(String value, String message) {
        assertThatThrownBy(() -> ContentRules.requireTitle(value))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .extracting(ex -> ((BusinessException) ex).data())
                .isEqualTo(Map.of("details", Map.of("title", message)));
    }
}
