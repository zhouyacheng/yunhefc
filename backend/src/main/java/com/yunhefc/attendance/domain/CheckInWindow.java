package com.yunhefc.attendance.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 签到窗口：开赛前 4 小时至比赛当天 23:59（Asia/Shanghai 推导）；由窗口驱动，不按比赛状态开关。
 */
public final class CheckInWindow {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration OPENS_BEFORE = Duration.ofHours(4);

    private CheckInWindow() {
    }

    public static Instant opensAt(Instant startAt) {
        return startAt.minus(OPENS_BEFORE);
    }

    /**
     * 比赛当天结束（次日 00:00 Asia/Shanghai），即“当天 23:59”的右开边界。
     */
    public static Instant closesAt(Instant startAt) {
        return startAt.atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public static boolean isOpen(Instant startAt, Instant now) {
        return !now.isBefore(opensAt(startAt)) && now.isBefore(closesAt(startAt));
    }
}
