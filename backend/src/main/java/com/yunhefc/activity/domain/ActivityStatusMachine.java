package com.yunhefc.activity.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 活动状态机：0↔1、0→2、1→2、0/1→3，以及已结束（2）→0/1（重新开放，P-110/P-111）。
 * 已取消（3）仍为终态、不可恢复；取消后重新组织活动走复制新建（P-99）。
 */
public final class ActivityStatusMachine {

    private static final Set<ActivityStatus> EDITABLE = Collections.unmodifiableSet(
            EnumSet.of(ActivityStatus.SIGNUP_OPEN, ActivityStatus.SIGNUP_CLOSED));

    private ActivityStatusMachine() {
    }

    /** 可编辑状态集合；SQL 条件与领域预检共用，避免两处清单漂移。 */
    public static Set<ActivityStatus> editable() {
        return EDITABLE;
    }

    public static void requireTransition(ActivityStatus from, ActivityStatus to) {
        boolean allowed = switch (from) {
            case SIGNUP_OPEN -> to == ActivityStatus.SIGNUP_CLOSED
                    || to == ActivityStatus.FINISHED
                    || to == ActivityStatus.CANCELLED;
            case SIGNUP_CLOSED -> to == ActivityStatus.SIGNUP_OPEN
                    || to == ActivityStatus.FINISHED
                    || to == ActivityStatus.CANCELLED;
            case FINISHED -> to == ActivityStatus.SIGNUP_OPEN
                    || to == ActivityStatus.SIGNUP_CLOSED;
            case CANCELLED -> false;
        };
        if (!allowed) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT,
                    "活动状态不允许从「" + from.label() + "」变更为「" + to.label() + "」");
        }
    }

    public static void requireEditable(ActivityStatus status) {
        if (!EDITABLE.contains(status)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT, "活动已结束或已取消，不可编辑");
        }
    }

    public static void requireRemovableSignup(ActivityStatus status) {
        if (!EDITABLE.contains(status)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT, "活动已结束或已取消，不可移除报名");
        }
    }
}
