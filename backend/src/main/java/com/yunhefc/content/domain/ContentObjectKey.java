package com.yunhefc.content.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 相册照片对象键：content/{uploaderUserId}/{ULID}.{jpg|png|webp}。
 * 只做纯格式拼装与校验；文件类型由调用方经 ImageSniffer 识别后传入扩展名。
 */
public final class ContentObjectKey {

    public static final String INVALID_MESSAGE = "照片对象键格式不正确";

    private static final Pattern PATTERN =
            Pattern.compile("^content/\\d+/[0-9A-HJKMNP-TV-Z]{26}\\.(jpg|png|webp)$");

    private ContentObjectKey() {
    }

    /**
     * 生成后立即按同一规则校验，保证返回值一定是合法对象键。
     */
    public static String generate(long uploaderUserId, String ulid, String extension) {
        return require("content/" + uploaderUserId + "/" + ulid + "." + extension);
    }

    public static String require(String objectKey) {
        if (objectKey == null || !PATTERN.matcher(objectKey).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(),
                    Map.of("details", Map.of("objectKey", INVALID_MESSAGE)));
        }
        return objectKey;
    }
}
