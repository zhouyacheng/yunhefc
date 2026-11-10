package com.yunhefc.match.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

/**
 * 比赛状态机：自动流转只前进；管理员手动重置可回到任意普通状态（含回退/终态恢复）。
 * POSTPONED 与 CANCELLED 分别由延期/取消专用流程处理，不属于普通状态。
 */
public final class MatchStatusMachine {

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

    /**
     * 自动流转只能进入更靠后的普通状态，不后退，也不进入延期/取消。
     */
    public static boolean canAutoAdvance(MatchStatus from, MatchStatus to) {
        return isOrdinary(from) && isOrdinary(to) && to.code() > from.code();
    }
}
