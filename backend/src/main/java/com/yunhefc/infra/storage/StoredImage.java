package com.yunhefc.infra.storage;

/**
 * 已上传的图片：对象键与 15 分钟签名 URL；discard 用于后续落库失败时 best-effort 回收对象。
 */
public final class StoredImage {

    private final String objectKey;
    private final String signedUrl;
    private final ObjectRemoval objectRemoval;

    StoredImage(String objectKey, String signedUrl, ObjectRemoval objectRemoval) {
        this.objectKey = objectKey;
        this.signedUrl = signedUrl;
        this.objectRemoval = objectRemoval;
    }

    public String objectKey() {
        return objectKey;
    }

    public String signedUrl() {
        return signedUrl;
    }

    public void discard() {
        objectRemoval.remove(objectKey);
    }
}
