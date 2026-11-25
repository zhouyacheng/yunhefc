package com.yunhefc.activity.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivityDomainTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 12, 20, 18, 0);

    @Test
    void allowsForwardTransitionsReopenAndCancel() {
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_OPEN, ActivityStatus.SIGNUP_CLOSED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_OPEN, ActivityStatus.FINISHED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_CLOSED, ActivityStatus.FINISHED)).doesNotThrowAnyException();
        // 报名截止可恢复为报名中（P-111）。
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_CLOSED, ActivityStatus.SIGNUP_OPEN)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_OPEN, ActivityStatus.CANCELLED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.SIGNUP_CLOSED, ActivityStatus.CANCELLED)).doesNotThrowAnyException();
        // 已结束可重新开放（P-110），已取消可恢复（P-114）。
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.FINISHED, ActivityStatus.SIGNUP_OPEN)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.FINISHED, ActivityStatus.SIGNUP_CLOSED)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.CANCELLED, ActivityStatus.SIGNUP_OPEN)).doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireTransition(
                ActivityStatus.CANCELLED, ActivityStatus.SIGNUP_CLOSED)).doesNotThrowAnyException();
    }

    @Test
    void rejectsIllegalTransitionsAndSameState() {
        assertStateConflict(ActivityStatus.CANCELLED, ActivityStatus.FINISHED);
        assertStateConflict(ActivityStatus.CANCELLED, ActivityStatus.CANCELLED);
        assertStateConflict(ActivityStatus.FINISHED, ActivityStatus.CANCELLED);
        assertStateConflict(ActivityStatus.FINISHED, ActivityStatus.FINISHED);
        assertStateConflict(ActivityStatus.SIGNUP_OPEN, ActivityStatus.SIGNUP_OPEN);
        assertStateConflict(ActivityStatus.SIGNUP_CLOSED, ActivityStatus.SIGNUP_CLOSED);
    }

    @Test
    void onlySignupOpenAndClosedAllowSignupRemoval() {
        assertThatCode(() -> ActivityStatusMachine.requireRemovableSignup(ActivityStatus.SIGNUP_OPEN))
                .doesNotThrowAnyException();
        assertThatCode(() -> ActivityStatusMachine.requireRemovableSignup(ActivityStatus.SIGNUP_CLOSED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ActivityStatusMachine.requireRemovableSignup(ActivityStatus.FINISHED))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ACTIVITY_STATE_CONFLICT);
        assertThatThrownBy(() -> ActivityStatusMachine.requireRemovableSignup(ActivityStatus.CANCELLED))
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
        assertViolation("locationImageKeys", () -> ActivityRules.validateImageKeys(List.of("avatar/1/x.jpg")));
        assertViolation("locationImageKeys", () -> ActivityRules.validateImageKeys(List.of("activity/1/abc.jpg")));
        String validKey = "activity/12/01JABCDEFGHJKMNPQRSTVWXYZ0.png";
        assertThatCode(() -> ActivityRules.validateImageKeys(List.of(validKey)))
                .doesNotThrowAnyException();
        assertViolation("locationImageKeys", () -> ActivityRules.validateImageKeys(
                List.of(validKey, validKey)));
        assertViolation("locationImageKeys", () -> ActivityRules.validateImageKeys(
                List.of(validKey, validKey, validKey, validKey, validKey, validKey,
                        validKey, validKey, validKey, validKey, validKey)));
        assertViolation("locationImageKeys", () -> ActivityRules.validateImageKeysPresent(null));
        assertThatCode(() -> ActivityRules.validateImageKeysPresent(List.of()))
                .doesNotThrowAnyException();
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
