package com.yunhefc.contracts.match;

import java.time.Instant;
import java.time.ZoneId;

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
        String awayTeamName,
        Instant statusUpdatedAt,
        String season,
        Integer type,
        Integer ourSide) {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int INTERNAL = 1;
    private static final int TRAINING = 2;

    /**
     * 汇总赛季：比赛 season 字段，为空时取开赛时间在 Asia/Shanghai 的年份。
     */
    public String effectiveSeason() {
        if (season != null && !season.isBlank()) {
            return season;
        }
        return startAt == null ? null : String.valueOf(startAt.atZone(BUSINESS_ZONE).getYear());
    }

    /**
     * 已取消比赛不计任何统计。
     */
    public boolean cancelled() {
        return status != null && status == MatchStatusCodes.CANCELLED;
    }

    /**
     * 队内赛/训练赛：球员可为主客任意球员录入事件。
     */
    public boolean internalOrTraining() {
        return type != null && (type == INTERNAL || type == TRAINING);
    }

    /**
     * 对外比赛我方所属队；非对外比赛我方主客为空时按主队处理。
     */
    public int ourEventTeam() {
        return ourSide == null ? 0 : ourSide;
    }

    /**
     * 未开赛：报名中/报名截止/锁定/已延期（04 §2.4 状态编码）；已延期通过重新排期回到报名中。
     */
    public boolean notStarted() {
        return status != null && (status == MatchStatusCodes.SIGNUP_OPEN
                || status == MatchStatusCodes.SIGNUP_CLOSED
                || status == MatchStatusCodes.LOCKED
                || status == MatchStatusCodes.POSTPONED);
    }

    public boolean signupOpen() {
        return status != null && status == MatchStatusCodes.SIGNUP_OPEN;
    }

    /**
     * 已结束：考勤缺勤判定与球员事件窗口只处理该状态的比赛。
     */
    public boolean finished() {
        return status != null && status == MatchStatusCodes.FINISHED;
    }

    /**
     * 事件录入仅进行中/已结束。
     */
    public boolean inProgressOrFinished() {
        return status != null && (status == MatchStatusCodes.IN_PROGRESS
                || status == MatchStatusCodes.FINISHED);
    }

    /**
     * 已取消/已延期：签到、事件等作废场次操作一律拒绝。
     */
    public boolean cancelledOrPostponed() {
        return status != null
                && (status == MatchStatusCodes.CANCELLED || status == MatchStatusCodes.POSTPONED);
    }

    /**
     * 球员可自行取消报名的状态：仅报名中与报名截止；锁定及之后、已取消、已延期都不允许。
     */
    public boolean cancellationOpen() {
        return status != null && (status == MatchStatusCodes.SIGNUP_OPEN
                || status == MatchStatusCodes.SIGNUP_CLOSED);
    }

    /**
     * 管理员移除报名：仅比赛结束或取消后不可移除，锁定、进行中与已延期仍可用。
     */
    public boolean removalOpen() {
        return status != null && (status == MatchStatusCodes.SIGNUP_OPEN
                || status == MatchStatusCodes.SIGNUP_CLOSED
                || status == MatchStatusCodes.LOCKED
                || status == MatchStatusCodes.IN_PROGRESS
                || status == MatchStatusCodes.POSTPONED);
    }
}
