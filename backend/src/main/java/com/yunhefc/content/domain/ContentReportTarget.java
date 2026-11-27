package com.yunhefc.content.domain;

/**
 * 举报目标：0-动态、1-照片；refType 写入通知的 ref_type（04 §2.4）。
 */
public enum ContentReportTarget {

    POST(0, "CONTENT_POST"),
    PHOTO(1, "CONTENT_PHOTO");

    private final int code;
    private final String refType;

    ContentReportTarget(int code, String refType) {
        this.code = code;
        this.refType = refType;
    }

    public int code() {
        return code;
    }

    public String refType() {
        return refType;
    }

    public static ContentReportTarget of(Integer code) {
        if (code != null) {
            for (ContentReportTarget target : values()) {
                if (target.code == code) {
                    return target;
                }
            }
        }
        throw ContentRules.validationError("targetType", "举报目标类型不合法");
    }
}
