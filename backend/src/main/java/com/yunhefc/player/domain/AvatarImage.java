package com.yunhefc.player.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.util.ImageSniffer;

/**
 * 头像文件的业务校验：魔数识别复用 {@link ImageSniffer}，头像额外限制 2 MiB。
 */
public final class AvatarImage {

    public static final long MAX_BYTES = 2_097_152L;
    public static final String EMPTY_MESSAGE = ImageSniffer.EMPTY_MESSAGE;
    public static final String TOO_LARGE_MESSAGE = "图片不能超过 2MB";
    public static final String CORRUPT_MESSAGE = ImageSniffer.CORRUPT_MESSAGE;

    private AvatarImage() {
    }

    public static ImageSniffer.Format validate(byte[] content) {
        if (content == null || content.length == 0) {
            throw invalidFile(EMPTY_MESSAGE);
        }
        if (content.length > MAX_BYTES) {
            throw invalidFile(TOO_LARGE_MESSAGE);
        }
        return ImageSniffer.sniff(content);
    }

    public static BusinessException invalidFile(String reason) {
        return ImageSniffer.invalidFile(reason);
    }
}
