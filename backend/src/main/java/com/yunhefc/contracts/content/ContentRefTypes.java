package com.yunhefc.contracts.content;

/**
 * 内容域 outbox aggregateType 与通知 ref_type（04 §2.4）。
 */
public final class ContentRefTypes {

    /** 动态。 */
    public static final String POST = "CONTENT_POST";
    /** 照片。 */
    public static final String PHOTO = "CONTENT_PHOTO";

    private ContentRefTypes() {
    }
}
