package com.yunhefc.player.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 头像文件的业务校验：只按文件头识别 JPEG/PNG/WebP，不信任扩展名与 Content-Type。
 */
public final class AvatarImage {

    public static final long MAX_BYTES = 2_097_152L;
    public static final String EMPTY_MESSAGE = "文件不能为空";
    public static final String TOO_LARGE_MESSAGE = "图片不能超过 2MB";
    public static final String CORRUPT_MESSAGE = "文件内容损坏";
    public static final String UNSUPPORTED_MESSAGE = "仅支持 JPG/PNG/WebP";

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] JPEG_END = {(byte) 0xFF, (byte) 0xD9};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = ascii("RIFF");
    private static final byte[] WEBP = ascii("WEBP");
    private static final byte[] IHDR = ascii("IHDR");
    private static final byte[] IEND = ascii("IEND");
    private static final byte[] VP8 = ascii("VP8 ");
    private static final byte[] VP8L = ascii("VP8L");
    private static final byte[] VP8X = ascii("VP8X");

    private AvatarImage() {
    }

    public enum Format {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    public static Format validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalidFile(EMPTY_MESSAGE);
        }
        if (content.length > MAX_BYTES) {
            throw invalidFile(TOO_LARGE_MESSAGE);
        }
        if (startsWith(content, JPEG_MAGIC)) {
            if (!contains(content, JPEG_END)) {
                throw invalidFile(CORRUPT_MESSAGE);
            }
            return Format.JPEG;
        }
        if (startsWith(content, PNG_MAGIC)) {
            if (!matchesAt(content, 12, IHDR) || !contains(content, IEND)) {
                throw invalidFile(CORRUPT_MESSAGE);
            }
            return Format.PNG;
        }
        if (startsWith(content, RIFF) && matchesAt(content, 8, WEBP)) {
            if (!matchesAt(content, 12, VP8) && !matchesAt(content, 12, VP8L) && !matchesAt(content, 12, VP8X)) {
                throw invalidFile(CORRUPT_MESSAGE);
            }
            return Format.WEBP;
        }
        throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, UNSUPPORTED_MESSAGE);
    }

    public static BusinessException invalidFile(String reason) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, "参数/字段校验失败",
                Map.of("details", Map.of("file", reason)));
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return matchesAt(content, 0, prefix);
    }

    private static boolean matchesAt(byte[] content, int offset, byte[] expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (content[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] content, byte[] target) {
        for (int offset = 0; offset <= content.length - target.length; offset++) {
            if (matchesAt(content, offset, target)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
