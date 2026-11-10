package com.yunhefc.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

class MatchStatusMachineTest {

    private static final EnumSet<MatchStatus> ORDINARY = EnumSet.of(
            MatchStatus.SIGNUP_OPEN,
            MatchStatus.SIGNUP_CLOSED,
            MatchStatus.LOCKED,
            MatchStatus.IN_PROGRESS,
            MatchStatus.FINISHED);

    @Test
    void ordinaryStatusesExcludePostponedAndCancelled() {
        for (MatchStatus status : MatchStatus.values()) {
            assertThat(MatchStatusMachine.isOrdinary(status))
                    .isEqualTo(ORDINARY.contains(status));
        }
    }

    @Test
    void manualTargetAcceptsAnyOrdinaryStatus() {
        ORDINARY.forEach(status -> assertThatCode(
                () -> MatchStatusMachine.requireManualTarget(status)).doesNotThrowAnyException());
    }

    @Test
    void manualTargetRejectsPostponedAndCancelled() {
        for (MatchStatus status : EnumSet.of(MatchStatus.POSTPONED, MatchStatus.CANCELLED)) {
            assertThatThrownBy(() -> MatchStatusMachine.requireManualTarget(status))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.errorCode()).isEqualTo(ErrorCode.MATCH_STATE_CONFLICT));
        }
    }

    @Test
    void postponeAcceptsOnlyPreMatchOrInProgressStatuses() {
        for (MatchStatus status : EnumSet.of(
                MatchStatus.SIGNUP_OPEN,
                MatchStatus.SIGNUP_CLOSED,
                MatchStatus.LOCKED,
                MatchStatus.IN_PROGRESS)) {
            assertThatCode(() -> MatchStatusMachine.requirePostponable(status))
                    .doesNotThrowAnyException();
        }

        for (MatchStatus status : EnumSet.of(
                MatchStatus.FINISHED,
                MatchStatus.POSTPONED,
                MatchStatus.CANCELLED)) {
            assertThatThrownBy(() -> MatchStatusMachine.requirePostponable(status))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.errorCode()).isEqualTo(ErrorCode.MATCH_STATE_CONFLICT));
        }
    }

    @Test
    void rescheduleAcceptsOnlyPostponed() {
        assertThatCode(() -> MatchStatusMachine.requireReschedulable(MatchStatus.POSTPONED))
                .doesNotThrowAnyException();

        for (MatchStatus status : EnumSet.complementOf(EnumSet.of(MatchStatus.POSTPONED))) {
            assertThatThrownBy(() -> MatchStatusMachine.requireReschedulable(status))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.errorCode()).isEqualTo(ErrorCode.MATCH_STATE_CONFLICT));
        }
    }

    @Test
    void cancelAcceptsEveryNonTerminalStatus() {
        for (MatchStatus status : EnumSet.complementOf(
                EnumSet.of(MatchStatus.FINISHED, MatchStatus.CANCELLED))) {
            assertThatCode(() -> MatchStatusMachine.requireCancellable(status))
                    .doesNotThrowAnyException();
        }

        for (MatchStatus status : EnumSet.of(MatchStatus.FINISHED, MatchStatus.CANCELLED)) {
            assertThatThrownBy(() -> MatchStatusMachine.requireCancellable(status))
                    .isInstanceOfSatisfying(BusinessException.class, ex ->
                            assertThat(ex.errorCode()).isEqualTo(ErrorCode.MATCH_STATE_CONFLICT));
        }
    }

    @Test
    void autoAdvanceOnlyMovesForwardBetweenOrdinaryStatuses() {
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.SIGNUP_OPEN, MatchStatus.SIGNUP_CLOSED)).isTrue();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.SIGNUP_OPEN, MatchStatus.IN_PROGRESS)).isTrue();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.LOCKED, MatchStatus.FINISHED)).isTrue();

        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.SIGNUP_CLOSED, MatchStatus.SIGNUP_OPEN)).isFalse();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.LOCKED, MatchStatus.LOCKED)).isFalse();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.FINISHED, MatchStatus.IN_PROGRESS)).isFalse();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.SIGNUP_OPEN, MatchStatus.POSTPONED)).isFalse();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.SIGNUP_OPEN, MatchStatus.CANCELLED)).isFalse();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.POSTPONED, MatchStatus.SIGNUP_OPEN)).isFalse();
        assertThat(MatchStatusMachine.canAutoAdvance(MatchStatus.CANCELLED, MatchStatus.SIGNUP_OPEN)).isFalse();
    }
}
