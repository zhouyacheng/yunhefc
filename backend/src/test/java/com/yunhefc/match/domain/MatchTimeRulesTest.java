package com.yunhefc.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;

class MatchTimeRulesTest {

    @Test
    void acceptsTwoToFourHourDurationsInclusive() {
        Instant start = local("2026-12-01T20:00:00");

        assertThat(MatchTimeRules.validateAndDerive(start, start.plus(Duration.ofHours(2))).endAt())
                .isEqualTo(start.plus(Duration.ofHours(2)));
        assertThat(MatchTimeRules.validateAndDerive(start, start.plus(Duration.ofHours(4))).endAt())
                .isEqualTo(start.plus(Duration.ofHours(4)));
    }

    @Test
    void rejectsDurationsOutsideTwoToFourHours() {
        Instant start = local("2026-12-01T20:00:00");

        assertRuleViolation(start, start.plus(Duration.ofHours(2)).minusMillis(1));
        assertRuleViolation(start, start.plus(Duration.ofHours(4)).plusMillis(1));
        assertRuleViolation(start, start);
    }

    @Test
    void startMustBeStrictlyAfterTenOClock() {
        Instant tenOClock = local("2026-12-01T10:00:00");
        Instant justAfter = LocalDateTime.ofInstant(tenOClock, MatchTimeRules.ZONE)
                .toLocalDate().atTime(LocalTime.of(10, 0, 0, 1))
                .atZone(MatchTimeRules.ZONE).toInstant();

        assertRuleViolation(tenOClock, tenOClock.plus(Duration.ofHours(2)));
        assertThat(MatchTimeRules.validateAndDerive(justAfter, justAfter.plus(Duration.ofHours(2)))
                .signupDeadline()).isEqualTo(local("2026-12-01T00:00:00"));
    }

    @Test
    void derivesDeadlineAndLockFromMatchDay() {
        MatchTimeRules.Schedule schedule = MatchTimeRules.validateAndDerive(
                local("2026-12-01T20:00:00"), local("2026-12-01T22:00:00"));

        assertThat(schedule.signupDeadline()).isEqualTo(local("2026-12-01T00:00:00"));
        assertThat(schedule.cancelLock()).isEqualTo(local("2026-12-01T10:00:00"));
    }

    private void assertRuleViolation(Instant start, Instant end) {
        assertThatThrownBy(() -> MatchTimeRules.validateAndDerive(start, end))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    private Instant local(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(MatchTimeRules.ZONE).toInstant();
    }
}
