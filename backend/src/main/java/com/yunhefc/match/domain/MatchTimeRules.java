package com.yunhefc.match.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 比赛时间规则：时长 2–4 小时，开赛晚于当天 10:00，截止/锁定按开赛当天推导。
 */
public final class MatchTimeRules {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final LocalTime EARLIEST_START = LocalTime.of(10, 0);
    private static final Duration MIN_DURATION = Duration.ofHours(2);
    private static final Duration MAX_DURATION = Duration.ofHours(4);

    private MatchTimeRules() {
    }

    public static Schedule validateAndDerive(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw violation("开始/结束时间不能为空");
        }
        Duration duration = Duration.between(startAt, endAt);
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
            throw violation("开始/结束时间相差必须为 2–4 小时");
        }
        LocalDate matchDay = startAt.atZone(ZONE).toLocalDate();
        if (!startAt.atZone(ZONE).toLocalTime().isAfter(EARLIEST_START)) {
            throw violation("开赛时间必须晚于当天 10:00");
        }
        return new Schedule(
                startAt,
                endAt,
                matchDay.atStartOfDay(ZONE).toInstant(),
                matchDay.atTime(EARLIEST_START).atZone(ZONE).toInstant());
    }

    private static BusinessException violation(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public record Schedule(Instant startAt, Instant endAt, Instant signupDeadline, Instant cancelLock) {
    }
}
