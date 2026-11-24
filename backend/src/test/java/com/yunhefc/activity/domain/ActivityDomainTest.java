package com.yunhefc.activity.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityDomainTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 20, 18, 0);

    @Test
    void allowsOnlyForwardTransitionsAndCancel() {
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_OPEN, ActivityStatus.SIGNUP_CLOSED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_OPEN, ActivityStatus.FINISHED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_CLOSED, ActivityStatus.FINISHED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_OPEN, ActivityStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_CLOSED, ActivityStatus.CANCELLED)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTransitionsFromTerminalOrSameState() {
        assertStateConflict(ActivityStatus.CANCELLED, ActivityStatus.SIGNUP_OPEN);
        assertStateConflict(ActivityStatus.CANCELLED, ActivityStatus.CANCELLED);
        assertStateConflict(ActivityStatus.FINISHED, ActivityStatus.SIGNUP_CLOSED);
        assertStateConflict(ActivityStatus.SIGNUP_OPEN, ActivityStatus.SIGNUP_OPEN);
        assertStateConflict(ActivityStatus.SIGNUP_CLOSED, ActivityStatus.SIGNUP_OPEN);
    }

    @Test
    void onlySignupOpenAndClosedAreEditable() {
        assertThatCode(() -> ActivityStatusMachine.requireEditable(ActivityStatus.SIGNUP_OPEN))
                .doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireEditable(ActivityStatus.SIGNUP_CLOSED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ActivityStatusMachine.requireEditable(ActivityStatus.FINISHED))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ACTIVITY_STATE_CONFLICT);
        assertThatThrownBy(() -> ActivityStatusMachine.requireEditable(ActivityStatus.CANCELLED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesCrossFieldRules() {
        assertThatCode(() -> ActivityRules.validateSchedule(START, START.plusHours(2),
                START.minusHours(1))).doesNotThrowAnyException();
        assertViolation("endAt", () -> ActivityRules.validateSchedule(START, START, START));
        assertViolation("signupDeadline", () -> ActivityRules.validateSchedule(START, null,
                START.plusMinutes(1)));
        assertViolation("capacity", () -> ActivityRules.validateCapacity(0));
        assertViolation("costPerPerson", () -> ActivityRules.validateCost(new BigDecimal("1.234")));
        assertViolation("costPerPerson", () -> ActivityRules.validateCost(new BigDecimal("-0.01")));
        assertViolation("locationImageKey", () -> ActivityRules.validateImageKey("avatar/1/x.jpg"));
        assertViolation("locationImageKey", () -> ActivityRules.validateImageKey("activity/1/abc.jpg"));
        assertThatCode(() -> ActivityRules.validateImageKey(
                "activity/12/01JABCDEFGHJKMNPQRSTVWXYZ0.png")).doesNotThrowAnyException();
    }

    @Test
    void signupOpenRequiresOpenStatusDeadlineAndCapacity() {
        TeamActivity activity = activity(ActivityStatus.SIGNUP_OPEN, START, 2);
        LocalDateTime beforeDeadline = START.minusHours(1);
        assertThat(ActivityRules.signupOpen(activity, 1, beforeDeadline)).isTrue();
        assertThat(ActivityRules.signupOpen(activity, 2, beforeDeadline)).isFalse();
        assertThat(ActivityRules.signupOpen(activity, 0, START.minusSeconds(1))).isTrue();
        assertThat(ActivityRules.signupOpen(activity, 0, START)).isFalse();
        activity.setCapacity(null);
        assertThat(ActivityRules.signupOpen(activity, 100, beforeDeadline)).isTrue();
        activity.setCapacity(2);
        activity.setStatus(ActivityStatus.SIGNUP_CLOSED);
        assertThat(ActivityRules.signupOpen(activity, 0, beforeDeadline)).isFalse();
    }

    private static void assertStateConflict(ActivityStatus from, ActivityStatus to) {
        assertThatThrownBy(() -> ActivityStatusMachine.requireTransition(from, to))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ACTIVITY_STATE_CONFLICT);
    }

    private static void assertViolation(String field, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException business = (BusinessException) ex;
                    assertThat(business.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(business.data().toString()).contains(field);
                });
    }

    private static TeamActivity activity(ActivityStatus status, LocalDateTime deadline, Integer capacity) {
        TeamActivity activity = new TeamActivity();
        activity.setStatus(status);
        activity.setSignupDeadline(deadline);
        activity.setCapacity(capacity);
        return activity;
    }
}
