package com.yunhefc.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

class EventRulesTest {

    @Test
    void substitutionRequiresDifferentInAndOutAndNoPlayer() {
        assertThatCode(() -> validate(EventType.SUBSTITUTION, null, null, 7L, 8L))
                .doesNotThrowAnyException();
        assertBusiness(() -> validate(EventType.SUBSTITUTION, null, null, 7L, 7L));
        assertBusiness(() -> validate(EventType.SUBSTITUTION, 6L, null, 7L, 8L));
        assertBusiness(() -> validate(EventType.SUBSTITUTION, null, null, 7L, null));
    }

    @Test
    void nonSubstitutionRequiresPlayerAndForbidsInOut() {
        assertBusiness(() -> validate(EventType.GOAL, null, null, null, null));
        assertBusiness(() -> validate(EventType.YELLOW_CARD, 6L, null, 7L, null));
        assertThatCode(() -> validate(EventType.YELLOW_CARD, 6L, null, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> validate(EventType.RED_CARD, 6L, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void assistAndPenaltyOnlyAllowedOnGoal() {
        assertThatCode(() -> validate(EventType.GOAL, 6L, 8L, null, null, true))
                .doesNotThrowAnyException();
        assertBusiness(() -> validate(EventType.GOAL, 6L, 6L, null, null));
        assertBusiness(() -> validate(EventType.OWN_GOAL, 6L, 8L, null, null));
        assertBusiness(() -> validate(EventType.OWN_GOAL, 6L, null, null, null, true));
        assertBusiness(() -> validate(EventType.YELLOW_CARD, 6L, 8L, null, null));
        assertBusiness(() -> validate(EventType.RED_CARD, 6L, null, null, null, true));
    }

    @Test
    void minuteMustBeOneToHundredTwenty() {
        assertValidation(() -> validate(EventType.GOAL, 6L, null, null, null, false, 0));
        assertValidation(() -> validate(EventType.GOAL, 6L, null, null, null, false, 121));
        assertThatCode(() -> validate(EventType.GOAL, 6L, null, null, null, false, 120))
                .doesNotThrowAnyException();
    }

    @Test
    void savedGroupingChecksEachPlayerFieldAgainstEventTeam() {
        SavedGroupingSnapshot snapshot = new SavedGroupingSnapshot(
                1L, Instant.now(), Map.of(6L, 0, 7L, 0, 8L, 1));
        assertThatCode(() -> EventRules.validateGrouping(
                fields(EventTeam.HOME, 6L, 7L, null, null), snapshot))
                .doesNotThrowAnyException();

        assertGrouping(() -> EventRules.validateGrouping(
                fields(EventTeam.HOME, 8L, null, null, null), snapshot), "playerId", 8L);
        assertGrouping(() -> EventRules.validateGrouping(
                fields(EventTeam.HOME, 6L, 8L, null, null), snapshot), "assistPlayerId", 8L);
        assertGrouping(() -> EventRules.validateGrouping(
                substitutionFields(EventTeam.HOME, 8L, 6L), snapshot), "inPlayerId", 8L);
        assertGrouping(() -> EventRules.validateGrouping(
                substitutionFields(EventTeam.HOME, 6L, 8L), snapshot), "outPlayerId", 8L);
    }

    @Test
    void ownGoalUsesEventTeamAndDraftGroupingDoesNotConstrain() {
        SavedGroupingSnapshot saved = new SavedGroupingSnapshot(
                1L, Instant.now(), Map.of(6L, 0, 8L, 1));
        assertThatCode(() -> EventRules.validateGrouping(
                fields(EventType.OWN_GOAL, EventTeam.HOME, 6L, null, null, null), saved))
                .doesNotThrowAnyException();
        assertGrouping(() -> EventRules.validateGrouping(
                fields(EventType.OWN_GOAL, EventTeam.HOME, 8L, null, null, null), saved),
                "playerId", 8L);

        SavedGroupingSnapshot draft = new SavedGroupingSnapshot(
                1L, null, Map.of(6L, 0, 8L, 1));
        assertThatCode(() -> EventRules.validateGrouping(
                fields(EventTeam.HOME, 8L, null, null, null), draft))
                .doesNotThrowAnyException();
    }

    private static EventRules.EventFields fields(EventTeam team, Long playerId, Long assistPlayerId,
                                                 Long inPlayerId, Long outPlayerId) {
        return fields(EventType.GOAL, team, playerId, assistPlayerId, inPlayerId, outPlayerId);
    }

    private static EventRules.EventFields fields(EventType type, EventTeam team, Long playerId,
                                                 Long assistPlayerId, Long inPlayerId,
                                                 Long outPlayerId) {
        return new EventRules.EventFields(type, team, playerId, assistPlayerId, inPlayerId,
                outPlayerId, 23, EventHalf.FIRST, false);
    }

    private static EventRules.EventFields substitutionFields(EventTeam team, Long inPlayerId,
                                                             Long outPlayerId) {
        return fields(EventType.SUBSTITUTION, team, null, null, inPlayerId, outPlayerId);
    }

    private static void assertGrouping(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            String field, Long playerId) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    assertThat(((BusinessException) ex).errorCode())
                            .isEqualTo(ErrorCode.GROUPING_MISMATCH);
                    assertThat(ex.getMessage())
                            .contains(field)
                            .contains(String.valueOf(playerId));
                });
    }

    private static void assertValidation(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static void validate(EventType type, Long playerId, Long assistPlayerId,
                                 Long inPlayerId, Long outPlayerId) {
        validate(type, playerId, assistPlayerId, inPlayerId, outPlayerId, false, 23);
    }

    private static void validate(EventType type, Long playerId, Long assistPlayerId,
                                 Long inPlayerId, Long outPlayerId, boolean penalty) {
        validate(type, playerId, assistPlayerId, inPlayerId, outPlayerId, penalty, 23);
    }

    private static void validate(EventType type, Long playerId, Long assistPlayerId,
                                 Long inPlayerId, Long outPlayerId, boolean penalty, int minute) {
        EventRules.validate(new EventRules.EventFields(
                type, EventTeam.HOME, playerId, assistPlayerId, inPlayerId, outPlayerId,
                minute, EventHalf.FIRST, penalty));
    }

    private static void assertBusiness(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode())
                        .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }
}
