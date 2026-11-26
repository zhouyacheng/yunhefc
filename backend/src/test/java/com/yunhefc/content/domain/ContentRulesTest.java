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

    @Test
    void postContentIsTrimmedBoundedAndKeepsLineBreaks() {
        assertThat(ContentRules.requirePostContent("  今天\n赢球了  ")).isEqualTo("今天\n赢球了");
        assertThat(ContentRules.requirePostContent("a".repeat(2000))).isEqualTo("a".repeat(2000));
        assertThatThrownBy(() -> ContentRules.requirePostContent(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ContentRules.requirePostContent("  "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ContentRules.requirePostContent("a".repeat(2001)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void reportReasonIsOptionalAndBounded() {
        assertThat(ContentRules.normalizeReportReason(null)).isNull();
        assertThat(ContentRules.normalizeReportReason("   ")).isNull();
        assertThat(ContentRules.normalizeReportReason("  含攻击性内容 ")).isEqualTo("含攻击性内容");
        assertThatThrownBy(() -> ContentRules.normalizeReportReason("a".repeat(201)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void reportTargetMapsCodesAndRejectsUnknown() {
        assertThat(ContentReportTarget.of(0)).isEqualTo(ContentReportTarget.POST);
        assertThat(ContentReportTarget.of(1)).isEqualTo(ContentReportTarget.PHOTO);
        assertThat(ContentReportTarget.POST.refType()).isEqualTo("CONTENT_POST");
        assertThat(ContentReportTarget.PHOTO.refType()).isEqualTo("CONTENT_PHOTO");
        assertThatThrownBy(() -> ContentReportTarget.of(2))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> ContentReportTarget.of(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED);
    }

    private void assertInvalidTitle(String value, String message) {
        assertThatThrownBy(() -> ContentRules.requireTitle(value))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_FAILED)
                .extracting(ex -> ((BusinessException) ex).data())
                .isEqualTo(Map.of("details", Map.of("title", message)));
    }
}
