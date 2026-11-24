package com.yunhefc.player.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.Map;

/**
 * 头像上传的体积校验；格式魔数识别由 application 层调用 {@code infra.storage.ImageSniffer}。
 */
public final class AvatarImage {

    public static final long MAX_BYTES = 5_242_880L;
    public static final String EMPTY_MESSAGE = "文件不能为空";
    public static final String TOO_LARGE_MESSAGE = "图片不能超过 5MB";

    private AvatarImage() {
    }

    public static void validateSize(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalidFile(EMPTY_MESSAGE);
        }
        if (content.length > MAX_BYTES) {
            throw invalidFile(TOO_LARGE_MESSAGE);
        }
    }

    public static BusinessException invalidFile(String reason) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(),
                Map.of("details", Map.of("file", reason)));
    }
}
