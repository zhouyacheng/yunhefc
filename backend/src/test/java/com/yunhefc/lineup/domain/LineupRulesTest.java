package com.yunhefc.lineup.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.match.MatchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LineupRulesTest {

    private static final List<String> SLOTS = List.of(
            "gk", "d1", "d2", "m1", "m2", "m3", "f1", "f2");

    @Test
    void opponentSideOnlyAppliesToExternalMatches() {
        assertThat(LineupRules.opponentSide(match(0, 0, 2), LineupRules.SIDE_HOME)).isFalse();
        assertThat(LineupRules.opponentSide(match(0, 0, 2), LineupRules.SIDE_AWAY)).isTrue();
        assertThat(LineupRules.opponentSide(match(3, 1, 2), LineupRules.SIDE_HOME)).isTrue();
        assertThat(LineupRules.opponentSide(match(3, 1, 2), LineupRules.SIDE_AWAY)).isFalse();
        assertThat(LineupRules.opponentSide(match(0, null, 2), LineupRules.SIDE_AWAY)).isTrue();

        assertThat(LineupRules.opponentSide(match(1, null, 2), LineupRules.SIDE_HOME)).isFalse();
        assertThat(LineupRules.opponentSide(match(1, null, 2), LineupRules.SIDE_AWAY)).isFalse();
        assertThat(LineupRules.opponentSide(match(2, null, 2), LineupRules.SIDE_AWAY)).isFalse();
    }

    @Test
    void onlyNonTerminalUnpostponedMatchesAreEditable() {
        assertThat(LineupRules.editable(match(1, null, 0))).isTrue();
        assertThat(LineupRules.editable(match(1, null, 1))).isTrue();
        assertThat(LineupRules.editable(match(1, null, 2))).isTrue();
        assertThat(LineupRules.editable(match(1, null, 3))).isTrue();
        assertThat(LineupRules.editable(match(1, null, 4))).isFalse();
        assertThat(LineupRules.editable(match(1, null, 5))).isFalse();
        assertThat(LineupRules.editable(match(1, null, 6))).isFalse();
        assertThat(LineupRules.editable(match(1, null, null))).isFalse();
    }

    @Test
    void ourSideRequiresEverySlotFilled() {
        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                starters(1L, 2L, 3L, 4L, 5L, 6L, 7L, null), List.of(), false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));

        LineupRules.validateSelection(SLOTS,
                starters(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), List.of(9L, 10L), false);
    }

    @Test
    void starterSlotsMustMatchFormation() {
        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                List.of(new LineupRules.Starter("gk", 1L)), List.of(), false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                List.of(
                        new LineupRules.Starter("gk", 1L),
                        new LineupRules.Starter("d1", 2L),
                        new LineupRules.Starter("d2", 3L),
                        new LineupRules.Starter("m1", 4L),
                        new LineupRules.Starter("m2", 5L),
                        new LineupRules.Starter("m3", 6L),
                        new LineupRules.Starter("f1", 7L),
                        new LineupRules.Starter("unknown", 8L)),
                List.of(), false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void samePlayerCannotAppearTwice() {
        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                starters(1L, 1L, 3L, 4L, 5L, 6L, 7L, 8L), List.of(), false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));

        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                starters(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), List.of(8L), false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void opponentSideOnlyAllowsEmptySlots() {
        LineupRules.validateSelection(SLOTS,
                starters(null, null, null, null, null, null, null, null), List.of(), true);

        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                starters(1L, null, null, null, null, null, null, null), List.of(), true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));

        assertThatThrownBy(() -> LineupRules.validateSelection(SLOTS,
                starters(null, null, null, null, null, null, null, null), List.of(1L), true))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode())
                                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION));
    }

    @Test
    void sideMustBeHomeOrAway() {
        assertThat(LineupRules.requireSide(0)).isZero();
        assertThat(LineupRules.requireSide(1)).isEqualTo(1);
        assertThatThrownBy(() -> LineupRules.requireSide(2))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    private static List<LineupRules.Starter> starters(Long... playerIds) {
        return java.util.stream.IntStream.range(0, SLOTS.size())
                .mapToObj(index -> new LineupRules.Starter(SLOTS.get(index), playerIds[index]))
                .toList();
    }

    private static MatchSnapshot match(int type, Integer ourSide, Integer status) {
        return new MatchSnapshot(1L, 1L, "测试比赛", null, null, null, null,
                30, 4, 2, "测试场地", status, "橙队", "兰队", null,
                "2026", type, ourSide, true);
    }
}
