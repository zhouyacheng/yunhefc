package com.yunhefc.player.domain;

import static com.yunhefc.AvatarFixtures.JPEG;
import static com.yunhefc.AvatarFixtures.PNG;
import static com.yunhefc.AvatarFixtures.WEBP;
import static com.yunhefc.AvatarFixtures.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

class AvatarImageTest {

    @Test
    void acceptsJpegPngAndWebpHeader() {
        assertThat(AvatarImage.validate(JPEG)).isEqualTo(AvatarImage.Format.JPEG);
        assertThat(AvatarImage.validate(JPEG).extension()).isEqualTo("jpg");
        assertThat(AvatarImage.validate(JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(AvatarImage.validate(PNG)).isEqualTo(AvatarImage.Format.PNG);
        assertThat(AvatarImage.validate(PNG).extension()).isEqualTo("png");
        assertThat(AvatarImage.validate(WEBP)).isEqualTo(AvatarImage.Format.WEBP);
        assertThat(AvatarImage.validate(WEBP).extension()).isEqualTo("webp");
    }

    @Test
    void rejectsEmptyFile() {
        assertValidation(() -> AvatarImage.validate(new byte[0]), "文件不能为空");
        assertValidation(() -> AvatarImage.validate(null), "文件不能为空");
    }

    @Test
    void rejectsFileOverTwoMebibytes() {
        byte[] content = new byte[(int) AvatarImage.MAX_BYTES + 1];
        System.arraycopy(JPEG, 0, content, 0, JPEG.length);
        assertValidation(() -> AvatarImage.validate(content), "图片不能超过 2MB");
    }

    @Test
    void acceptsExactlyTwoMebibytes() {
        byte[] content = new byte[(int) AvatarImage.MAX_BYTES];
        System.arraycopy(JPEG, 0, content, 0, JPEG.length);
        assertThat(AvatarImage.validate(content)).isEqualTo(AvatarImage.Format.JPEG);
    }

    @Test
    void rejectsTruncatedImageHeader() {
        assertValidation(() -> AvatarImage.validate(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}),
                "文件内容损坏");
        assertValidation(() -> AvatarImage.validate(new byte[] {
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00}),
                "文件内容损坏");
        assertValidation(() -> AvatarImage.validate(concat(
                        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                        new byte[] {0x00, 0x00, 0x00, 0x0D},
                        "NOPE".getBytes(StandardCharsets.US_ASCII))),
                "文件内容损坏");
        assertValidation(() -> AvatarImage.validate(concat(
                        "RIFF".getBytes(StandardCharsets.US_ASCII),
                        new byte[] {0x04, 0x00, 0x00, 0x00},
                        "WEBP".getBytes(StandardCharsets.US_ASCII))),
                "文件内容损坏");
    }

    @Test
    void rejectsDisguisedNonImageAsUnsupportedMediaType() {
        byte[] pdf = "%PDF-1.7 not an image".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> AvatarImage.validate(pdf))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ex.getMessage()).isEqualTo("仅支持 JPG/PNG/WebP");
                });

        byte[] gif = "GIF89a-not-supported".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> AvatarImage.validate(gif))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        // 短于任何图片头也必须按“非支持类型”处理，而不是误报损坏。
        byte[] shortGif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> AvatarImage.validate(shortGif))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        byte[] unknown = {0x01, 0x02, 0x03};
        assertThatThrownBy(() -> AvatarImage.validate(unknown))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    private void assertValidation(Runnable call, String reason) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(ex.data()).isEqualTo(Map.of("details", Map.of("file", reason)));
                });
    }

}
