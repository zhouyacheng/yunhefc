package com.yunhefc.activity.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

/**
 * 活动状态机：0↔1、0/1→2、0/1→3、2→0/1（P-110）、3→0/1（P-114）。
 * 已取消可恢复为报名中/报名截止，但不可变为已结束；字段编辑与状态解耦，
 * 任意状态均可编辑活动字段（P-113）；报名移除仍仅限报名中/报名截止。
 */
public final class ActivityStatusMachine {

    private ActivityStatusMachine() {
    }

    public static void requireTransition(ActivityStatus from, ActivityStatus to) {
        boolean allowed = switch (from) {
            case SIGNUP_OPEN -> to == ActivityStatus.SIGNUP_CLOSED
                    || to == ActivityStatus.FINISHED
                    || to == ActivityStatus.CANCELLED;
            case SIGNUP_CLOSED -> to == ActivityStatus.SIGNUP_OPEN
                    || to == ActivityStatus.FINISHED
                    || to == ActivityStatus.CANCELLED;
            case FINISHED, CANCELLED -> to == ActivityStatus.SIGNUP_OPEN
                    || to == ActivityStatus.SIGNUP_CLOSED;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT,
                    "活动状态不允许从「" + from.label() + "」变更为「" + to.label() + "」");
        }
    }

    public static void requireRemovableSignup(ActivityStatus status) {
        if (status != ActivityStatus.SIGNUP_OPEN && status != ActivityStatus.SIGNUP_CLOSED) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT, "活动已结束或已取消，不可移除报名");
        }
    }
}
