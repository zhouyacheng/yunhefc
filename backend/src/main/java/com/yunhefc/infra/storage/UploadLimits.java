package com.yunhefc.infra.storage;

/**
 * 上传端点的应用层体积上限；全局 multipart 按最大值放行，由各端点自行兜住。头像 5 MiB 由
 * {@code player.domain.AvatarImage} 单独校验。
 */
public final class UploadLimits {

    public static final long ACTIVITY_IMAGE_MAX_BYTES = 5_242_880L;

    private UploadLimits() {
    }
}
