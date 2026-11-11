package com.yunhefc.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

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
