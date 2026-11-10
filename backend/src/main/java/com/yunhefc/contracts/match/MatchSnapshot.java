package com.yunhefc.contracts.match;

import java.time.Instant;

/**
 * 跨模块读取的比赛快照。
 */
public record MatchSnapshot(
        Long id,
        Long teamId,
        String title,
        Instant startAt,
        Instant endAt,
        Instant signupDeadline,
        Instant cancelLock,
        Integer capacity,
        Integer guestCapacity,
        Integer waitlistCapacity,
        String venue,
        Integer status,
        String homeTeamName,
        String awayTeamName) {

    private static final int SIGNUP_OPEN = 0;
    private static final int SIGNUP_CLOSED = 1;
    private static final int LOCKED = 2;
    private static final int IN_PROGRESS = 3;
    private static final int POSTPONED = 5;

    /**
     * 未开赛：报名中/报名截止/锁定/已延期（04 §2.4 状态编码）；已延期通过重新排期回到报名中。
     */
    public boolean notStarted() {
        return status != null && (status == 0 || status == 1 || status == 2 || status == 5);
    }

    public boolean signupOpen() {
        return status != null && status == SIGNUP_OPEN;
    }

    /**
     * 球员可自行取消报名的状态：仅报名中与报名截止；锁定及之后、已取消、已延期都不允许。
     */
    public boolean cancellationOpen() {
        return status != null && (status == SIGNUP_OPEN || status == SIGNUP_CLOSED);
    }

    /**
     * 管理员移除报名：仅比赛结束或取消后不可移除，锁定、进行中与已延期仍可用。
     */
    public boolean removalOpen() {
        return status != null && (status == SIGNUP_OPEN || status == SIGNUP_CLOSED
                || status == LOCKED || status == IN_PROGRESS || status == POSTPONED);
    }
}
