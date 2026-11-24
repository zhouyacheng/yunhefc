package com.yunhefc.player.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

class AvatarImageTest {

    @Test
    void rejectsEmptyFile() {
        assertValidation(() -> AvatarImage.validateSize(new byte[0]), "文件不能为空");
        assertValidation(() -> AvatarImage.validateSize(null), "文件不能为空");
    }

    @Test
    void rejectsFileOverTwoMebibytes() {
        byte[] content = new byte[(int) AvatarImage.MAX_BYTES + 1];
        assertValidation(() -> AvatarImage.validateSize(content), "图片不能超过 2MB");
    }

    @Test
    void acceptsExactlyTwoMebibytes() {
        byte[] content = new byte[(int) AvatarImage.MAX_BYTES];
        assertThatCode(() -> AvatarImage.validateSize(content)).doesNotThrowAnyException();
    }

    private void assertValidation(Runnable call, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.data()).isEqualTo(Map.of("details", Map.of("file", reason)));
                });
    }
}
