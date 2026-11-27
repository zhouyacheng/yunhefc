package com.yunhefc.content.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.Map;

/**
 * 内容域字段与容量规则；数据库禁用 CHECK，全部由应用层强校验（04 §8.1）。
 */
public final class ContentRules {

    /** 05 CNT-03：网格与列表的缩略图走签名 URL 的 x-oss-process，详情签原图。 */
    public static final String THUMBNAIL_PROCESS = "image/resize,m_lfit,w_480/quality,q_80";

    /** 单相册有效照片上限；第 201 张返回 40920。 */
    public static final int MAX_PHOTOS_PER_ALBUM = 200;

    /** 05 CNT-10：动态正文去除首尾空白后 1–2000 字，允许换行。 */
    public static final int POST_MAX_LENGTH = 2000;

    /** 05 ContentReportCreate：举报原因可选，≤200 字。 */
    public static final int REPORT_REASON_MAX_LENGTH = 200;

    private static final int TITLE_MAX_LENGTH = 64;

    private ContentRules() {
    }

    /**
     * 动态正文：去除首尾空白后 1–2000 字，内部换行保留。
     */
    public static String requirePostContent(String raw) {
        String content = raw == null ? null : raw.strip();
        if (content == null || content.isEmpty()) {
            throw validationError("content", "动态正文不能为空");
        }
        if (content.length() > POST_MAX_LENGTH) {
            throw validationError("content", "动态正文不能超过 " + POST_MAX_LENGTH + " 个字符");
        }
        return content;
    }

    /**
     * 举报原因可选：空白归一为 null，超长返回 40001。
     */
    public static String normalizeReportReason(String raw) {
        String reason = raw == null ? null : raw.strip();
        if (reason == null || reason.isEmpty()) {
            return null;
        }
        if (reason.length() > REPORT_REASON_MAX_LENGTH) {
            throw validationError("reason", "举报原因不能超过 " + REPORT_REASON_MAX_LENGTH + " 个字符");
        }
        return reason;
    }

    /**
     * 相册名称：去除首尾空白后 1–64 字符。
     */
    public static String requireTitle(String raw) {
        String title = raw == null ? null : raw.strip();
        if (title == null || title.isEmpty()) {
            throw validationError("title", "相册名称不能为空");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw validationError("title", "相册名称不能超过 " + TITLE_MAX_LENGTH + " 个字符");
        }
        return title;
    }

    /**
     * 相册至多关联一个比赛或活动。
     */
    public static void requireAtMostOneOwner(Long matchId, Long activityId) {
        if (matchId != null && activityId != null) {
            throw validationError("matchId", "比赛与活动只能关联一个");
        }
    }

    /**
     * 上传前的容量门禁：已达上限时拒绝第 201 张。
     */
    public static void requirePhotoSlot(int activePhotos) {
        if (activePhotos >= MAX_PHOTOS_PER_ALBUM) {
            throw new BusinessException(ErrorCode.CONTENT_ALBUM_FULL);
        }
    }

    public static BusinessException validationError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(),
                Map.of("details", Map.of(field, message)));
    }
}
