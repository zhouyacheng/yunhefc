package com.yunhefc.match.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.util.EnumSet;
import java.util.Set;

/**
 * 比赛状态机：自动流转只前进；管理员手动重置可回到任意普通状态（含回退/终态恢复）。
 * POSTPONED 与 CANCELLED 分别由延期/取消专用流程处理，不属于普通状态。
 */
public final class MatchStatusMachine {

    private static final Set<MatchStatus> POSTPONABLE = EnumSet.of(
            MatchStatus.SIGNUP_OPEN,
            MatchStatus.SIGNUP_CLOSED,
            MatchStatus.LOCKED,
            MatchStatus.IN_PROGRESS);

    private MatchStatusMachine() {
    }

    public static boolean isOrdinary(MatchStatus status) {
        return status != MatchStatus.POSTPONED && status != MatchStatus.CANCELLED;
    }

    public static void requireManualTarget(MatchStatus target) {
        if (!isOrdinary(target)) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT,
                    target == MatchStatus.POSTPONED
                            ? "延期请使用延期接口"
                            : "取消请使用取消接口");
        }
    }

    public static void requirePostponable(MatchStatus status) {
        if (!POSTPONABLE.contains(status)) {
            throw stateConflict("当前状态不可延期");
        }
    }

    public static void requireReschedulable(MatchStatus status) {
        if (status != MatchStatus.POSTPONED) {
            throw stateConflict("仅已延期比赛可重新排期");
        }
    }

    public static void requireCancellable(MatchStatus status) {
        if (status == MatchStatus.FINISHED || status == MatchStatus.CANCELLED) {
            throw stateConflict("终态比赛不可取消");
        }
    }

    /**
     * 自动流转只能进入更靠后的普通状态，不后退，也不进入延期/取消。
     */
    public static boolean canAutoAdvance(MatchStatus from, MatchStatus to) {
        return isOrdinary(from) && isOrdinary(to) && to.code() > from.code();
    }

    private static BusinessException stateConflict(String message) {
        return new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, message);
    }
}
