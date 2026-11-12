package com.yunhefc.player.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 头像写入口的对象键校验；读响应由 {@code infra.storage.AvatarUrlSigner} 现签 URL。
 */
public final class AvatarObjectKey {

    public static final String INVALID_MESSAGE = "头像对象键格式不正确";

    private static final Pattern PATTERN =
            Pattern.compile("^avatar/\\d+/[0-9A-HJKMNP-TV-Z]{26}\\.(jpg|png|webp)$");

    private AvatarObjectKey() {
    }

    /**
     * null 与空白串归一为 null（清空头像）；其余必须原样匹配上传接口返回的对象键。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!PATTERN.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(),
                    Map.of("details", Map.of("avatarUrl", INVALID_MESSAGE)));
        }
        return raw;
    }
}
