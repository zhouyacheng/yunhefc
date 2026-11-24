package com.yunhefc.activity.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 编辑时显式出现的图片数组不得为 null；缺省保留、空数组清空。 */
    public static void validateImageKeysPresent(List<String> imageKeys) {
        if (imageKeys == null) {
            throw invalid("locationImageKeys", "地点图片不能为 null");
        }
        validateImageKeys(imageKeys);
    }

    /** 0–10 张、顺序即展示顺序、不可重复；逐项只校验对象键格式。 */
    public static void validateImageKeys(List<String> imageKeys) {
        if (imageKeys == null) {
            return;
        }
        if (imageKeys.size() > 10) {
            throw invalid("locationImageKeys", "地点图片最多 10 张");
        }
        Set<String> seen = new HashSet<>();
        for (String objectKey : imageKeys) {
            if (objectKey == null || !IMAGE_KEY.matcher(objectKey).matches()) {
                throw invalid("locationImageKeys", "图片对象键不合法");
            }
            if (!seen.add(objectKey)) {
                throw invalid("locationImageKeys", "地点图片不能重复");
            }
        }
    }

    /** 活动级家属开关只约束新报名；关闭时拒绝携带家属，不回溯已有报名。 */
    public static void validateFamilyAllowed(TeamActivity activity, boolean withFamily) {
        if (withFamily && !activity.isFamilyAllowed()) {
            throw invalid("withFamily", "该活动不允许携带家属");
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
