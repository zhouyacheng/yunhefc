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

    /**
     * 未开赛：报名中/报名截止/锁定/已延期（04 §2.4 状态编码）；已延期通过重新排期回到报名中。
     */
    public boolean notStarted() {
        return status != null && (status == 0 || status == 1 || status == 2 || status == 5);
    }

    public boolean signupOpen() {
        return status != null && status == SIGNUP_OPEN;
    }
}
