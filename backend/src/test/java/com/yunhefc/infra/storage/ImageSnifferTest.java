package com.yunhefc.infra.storage;

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

class ImageSnifferTest {

    @Test
    void acceptsJpegPngAndWebpHeader() {
        assertThat(ImageSniffer.sniff(JPEG)).isEqualTo(ImageSniffer.Format.JPEG);
        assertThat(ImageSniffer.sniff(JPEG).extension()).isEqualTo("jpg");
        assertThat(ImageSniffer.sniff(JPEG).contentType()).isEqualTo("image/jpeg");
        assertThat(ImageSniffer.sniff(PNG)).isEqualTo(ImageSniffer.Format.PNG);
        assertThat(ImageSniffer.sniff(PNG).extension()).isEqualTo("png");
        assertThat(ImageSniffer.sniff(WEBP)).isEqualTo(ImageSniffer.Format.WEBP);
        assertThat(ImageSniffer.sniff(WEBP).extension()).isEqualTo("webp");
    }

    @Test
    void rejectsEmptyFile() {
        assertValidation(() -> ImageSniffer.sniff(new byte[0]), "文件不能为空");
        assertValidation(() -> ImageSniffer.sniff(null), "文件不能为空");
    }

    @Test
    void rejectsTruncatedImageHeader() {
        assertValidation(() -> ImageSniffer.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}),
                "文件内容损坏");
        assertValidation(() -> ImageSniffer.sniff(new byte[] {
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00}),
                "文件内容损坏");
        assertValidation(() -> ImageSniffer.sniff(concat(
                        new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                        new byte[] {0x00, 0x00, 0x00, 0x0D},
                        "NOPE".getBytes(StandardCharsets.US_ASCII))),
                "文件内容损坏");
        assertValidation(() -> ImageSniffer.sniff(concat(
                        "RIFF".getBytes(StandardCharsets.US_ASCII),
                        new byte[] {0x04, 0x00, 0x00, 0x00},
                        "WEBP".getBytes(StandardCharsets.US_ASCII))),
                "文件内容损坏");
    }

    @Test
    void rejectsDisguisedNonImageAsUnsupportedMediaType() {
        byte[] pdf = "%PDF-1.7 not an image".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> ImageSniffer.sniff(pdf))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                    assertThat(ex.getMessage()).isEqualTo("仅支持 JPG/PNG/WebP");
                });

        byte[] gif = "GIF89a-not-supported".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> ImageSniffer.sniff(gif))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        // 短于任何图片头也必须按“非支持类型”处理，而不是误报损坏。
        byte[] shortGif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> ImageSniffer.sniff(shortGif))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        byte[] unknown = {0x01, 0x02, 0x03};
        assertThatThrownBy(() -> ImageSniffer.sniff(unknown))
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
