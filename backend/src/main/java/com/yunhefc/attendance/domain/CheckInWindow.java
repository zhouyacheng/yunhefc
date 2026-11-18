package com.yunhefc.attendance.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 签到窗口：开赛前 4 小时至比赛当天 23:59（Asia/Shanghai 推导）；由窗口驱动，不按比赛状态开关。
 */
public final class CheckInWindow {

    private static final Duration OPENS_BEFORE = Duration.ofHours(4);

    private CheckInWindow() {
    }

    public static LocalDateTime opensAt(LocalDateTime startAt) {
        return startAt.minus(OPENS_BEFORE);
    }

    /**
     * 比赛当天结束（次日 00:00 Asia/Shanghai），即“当天 23:59”的右开边界。
     */
    public static LocalDateTime closesAt(LocalDateTime startAt) {
        return startAt.toLocalDate().plusDays(1).atStartOfDay();
    }

    public static boolean isOpen(LocalDateTime startAt, LocalDateTime now) {
        return !now.isBefore(opensAt(startAt)) && now.isBefore(closesAt(startAt));
    }
}
