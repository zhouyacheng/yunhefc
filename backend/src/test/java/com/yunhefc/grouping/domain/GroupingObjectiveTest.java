package com.yunhefc.grouping.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingObjectiveTest {

    @Test
    void goalkeeperFeasibilityOutranksTheOtherCriteria() {
        GroupingObjective missingGoalkeeper = new GroupingObjective(true, false,
                BigDecimal.ZERO, 0);
        GroupingObjective balanced = new GroupingObjective(false, false,
                BigDecimal.valueOf(10), 3);

        assertThat(missingGoalkeeper.compareTo(balanced)).isPositive();
        assertThat(balanced.compareTo(missingGoalkeeper)).isNegative();
    }

    @Test
    void comparesCountThenScoreThenPositionDiff() {
        GroupingObjective countGap = new GroupingObjective(false, true,
                BigDecimal.ZERO, 0);
        GroupingObjective scoreOnly = new GroupingObjective(false, false,
                BigDecimal.valueOf(99), 9);
        assertThat(countGap.compareTo(scoreOnly)).isPositive();

        GroupingObjective lowerScore = new GroupingObjective(false, false,
                BigDecimal.ONE, 5);
        GroupingObjective higherScore = new GroupingObjective(false, false,
                BigDecimal.TEN, 0);
        assertThat(lowerScore.compareTo(higherScore)).isNegative();

        GroupingObjective lowerPosition = new GroupingObjective(false, false,
                BigDecimal.ONE, 1);
        GroupingObjective higherPosition = new GroupingObjective(false, false,
                BigDecimal.ONE, 2);
        assertThat(lowerPosition.compareTo(higherPosition)).isNegative();
    }

    @Test
    void evaluatesMissingGoalkeeperOnlyWhenPoolHasAtLeastTwo() {
        List<AssignedPlayer> noGoalkeeper = List.of(player(1L, 1, "3.00"));
        List<AssignedPlayer> other = List.of(player(2L, 1, "3.00"));

        assertThat(GroupingObjective.evaluate(noGoalkeeper, other, 1).missingGoalkeeper())
                .isFalse();
        assertThat(GroupingObjective.evaluate(
                List.of(player(1L, 0, "3.00"), player(2L, 0, "3.00")), List.of(), 2)
                .missingGoalkeeper()).isTrue();
    }

    private static AssignedPlayer player(Long id, int position, String finalScore) {
        GroupingCandidate candidate = new GroupingCandidate(id, "球员" + id, id.intValue(), position, 3);
        GroupingScore score = new GroupingScore(id, BigDecimal.valueOf(3).setScale(1), null,
                new BigDecimal(finalScore), true);
        return new AssignedPlayer(candidate, score);
    }
}
