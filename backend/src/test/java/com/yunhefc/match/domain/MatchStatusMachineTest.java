package com.yunhefc.match.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;

class MatchStatusMachineTest {

    private static final Instant SIGNUP_DEADLINE = Instant.parse("2026-03-01T16:00:00Z");
    private static final Instant CANCEL_LOCK = Instant.parse("2026-03-02T02:00:00Z");
    private static final Instant START_AT = Instant.parse("2026-03-02T12:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-03-02T14:00:00Z");

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
    void autoTargetAdvancesAtEachBoundary() {
        assertThat(autoTarget(MatchStatus.SIGNUP_OPEN, false, SIGNUP_DEADLINE.minusSeconds(1)))
                .isEqualTo(MatchStatus.SIGNUP_OPEN);
        assertThat(autoTarget(MatchStatus.SIGNUP_OPEN, false, SIGNUP_DEADLINE))
                .isEqualTo(MatchStatus.SIGNUP_CLOSED);
        assertThat(autoTarget(MatchStatus.SIGNUP_CLOSED, false, CANCEL_LOCK))
                .isEqualTo(MatchStatus.LOCKED);
        assertThat(autoTarget(MatchStatus.LOCKED, false, START_AT))
                .isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(autoTarget(MatchStatus.IN_PROGRESS, false, END_AT))
                .isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    void autoTargetCatchesUpToHighestReachedStatus() {
        assertThat(autoTarget(MatchStatus.SIGNUP_OPEN, false, END_AT.plusSeconds(1)))
                .isEqualTo(MatchStatus.FINISHED);
        assertThat(autoTarget(MatchStatus.SIGNUP_OPEN, false, START_AT))
                .isEqualTo(MatchStatus.IN_PROGRESS);
    }

    @Test
    void autoTargetNeverMovesBackwards() {
        assertThat(autoTarget(MatchStatus.LOCKED, false, SIGNUP_DEADLINE))
                .isEqualTo(MatchStatus.LOCKED);
        assertThat(autoTarget(MatchStatus.IN_PROGRESS, false, CANCEL_LOCK))
                .isEqualTo(MatchStatus.IN_PROGRESS);
        assertThat(autoTarget(MatchStatus.FINISHED, false, START_AT))
                .isEqualTo(MatchStatus.FINISHED);
    }

    @Test
    void autoTargetSkipsManualPostponedCancelledAndFinished() {
        assertThat(autoTarget(MatchStatus.SIGNUP_OPEN, true, END_AT.plusSeconds(1)))
                .isEqualTo(MatchStatus.SIGNUP_OPEN);
        assertThat(autoTarget(MatchStatus.POSTPONED, false, END_AT.plusSeconds(1)))
                .isEqualTo(MatchStatus.POSTPONED);
        assertThat(autoTarget(MatchStatus.CANCELLED, false, END_AT.plusSeconds(1)))
                .isEqualTo(MatchStatus.CANCELLED);
        assertThat(autoTarget(MatchStatus.FINISHED, false, END_AT.plusSeconds(1)))
                .isEqualTo(MatchStatus.FINISHED);
    }

    private MatchStatus autoTarget(MatchStatus current, boolean manualStatus, Instant now) {
        return MatchStatusMachine.autoTarget(current, manualStatus, now,
                SIGNUP_DEADLINE, CANCEL_LOCK, START_AT, END_AT);
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
