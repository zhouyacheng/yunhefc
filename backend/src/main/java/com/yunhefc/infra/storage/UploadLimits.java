package com.yunhefc.infra.storage;

/**
 * 各上传端点的应用层体积上限；全局 multipart 上限按最大值放行，由各端点自行兜住。
 */
public final class UploadLimits {

    public static final long ACTIVITY_IMAGE_MAX_BYTES = 5_242_880L;

    private UploadLimits() {
    }
}
