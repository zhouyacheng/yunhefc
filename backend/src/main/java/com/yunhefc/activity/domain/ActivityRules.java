package com.yunhefc.activity.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 活动字段与报名门禁规则；数据库禁用 CHECK，全部由应用层强校验（04 §8.1）。
 */
public final class ActivityRules {

    /** FIL-03 生成的对象键：activity/{uploaderUserId}/{ULID}.{ext}，只校验格式与前缀。 */
    private static final Pattern IMAGE_KEY =
            Pattern.compile("^activity/\\d+/[0-9A-HJKMNP-TV-Z]{26}\\.(jpg|png|webp)$");

    private ActivityRules() {
    }

    public static void validateSchedule(LocalDateTime startAt, LocalDateTime endAt,
                                        LocalDateTime signupDeadline) {
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw invalid("endAt", "结束时间必须晚于开始时间");
        }
        if (signupDeadline.isAfter(startAt)) {
            throw invalid("signupDeadline", "报名截止不能晚于开始时间");
        }
    }

    public static void validateCapacity(Integer capacity) {
        if (capacity != null && capacity < 1) {
            throw invalid("capacity", "人数上限至少为 1");
        }
    }

    public static void validateCost(BigDecimal cost) {
        if (cost == null) {
            return;
        }
        if (cost.signum() < 0) {
            throw invalid("costPerPerson", "人均消费不能为负数");
        }
        if (cost.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw invalid("costPerPerson", "人均消费金额过大");
        }
        if (cost.stripTrailingZeros().scale() > 2) {
            throw invalid("costPerPerson", "人均消费最多两位小数");
        }
    }

    public static void validateImageKey(String objectKey) {
        if (objectKey != null && !IMAGE_KEY.matcher(objectKey).matches()) {
            throw invalid("locationImageKey", "图片对象键不合法");
        }
    }

    /** 报名硬门禁：报名中、未过截止且未满员；capacity 为 NULL 视为不限。 */
    public static boolean signupOpen(TeamActivity activity, int signupCount, LocalDateTime now) {
        return withinSignupWindow(activity, now)
                && (activity.getCapacity() == null || signupCount < activity.getCapacity());
    }

    public static boolean withinSignupWindow(TeamActivity activity, LocalDateTime now) {
        return activity.getStatus() == ActivityStatus.SIGNUP_OPEN
                && now.isBefore(activity.getSignupDeadline());
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(), Map.of("details", Map.of(field, message)));
    }
}
