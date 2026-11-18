package com.yunhefc.grouping.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingScoreCalculatorTest {

    @Test
    void usesPlayerSkillAsManualAndFinalScore() {
        GroupingScore score = GroupingScoreCalculator.calculate(List.of(candidate(1L, "4.25")))
                .getFirst();

        assertThat(score.playerId()).isEqualTo(1L);
        assertThat(score.manualScore()).isEqualByComparingTo("4.25");
        assertThat(score.autoScore()).isNull();
        assertThat(score.finalScore()).isEqualByComparingTo("4.25");
    }

    @Test
    void defaultsToThreeWhenSkillIsMissing() {
        GroupingScore score = GroupingScoreCalculator.calculate(
                        List.of(candidate(1L, (BigDecimal) null)))
                .getFirst();

        assertThat(score.manualScore()).isEqualByComparingTo("3.00");
        assertThat(score.finalScore()).isEqualByComparingTo("3.00");
    }

    @Test
    void clampsSkillToOneToFive() {
        List<GroupingScore> scores = GroupingScoreCalculator.calculate(
                List.of(candidate(1L, "0.50"), candidate(2L, "5.80")));

        assertThat(scores.get(0).finalScore()).isEqualByComparingTo("1.00");
        assertThat(scores.get(1).finalScore()).isEqualByComparingTo("5.00");
    }

    @Test
    void keepsTwoDecimalScale() {
        GroupingScore score = GroupingScoreCalculator.calculate(List.of(candidate(1L, "4.5")))
                .getFirst();

        assertThat(score.manualScore().scale()).isEqualTo(2);
        assertThat(score.finalScore().scale()).isEqualTo(2);
    }

    @Test
    void preservesCandidateOrder() {
        List<GroupingScore> scores = GroupingScoreCalculator.calculate(
                List.of(candidate(2L, "3.75"), candidate(1L, "4.00")));

        assertThat(scores).extracting(GroupingScore::playerId).containsExactly(2L, 1L);
        assertThat(scores.get(0).finalScore()).isEqualByComparingTo("3.75");
    }

    private static GroupingCandidate candidate(Long id, String skill) {
        return candidate(id, new BigDecimal(skill));
    }

    private static GroupingCandidate candidate(Long id, BigDecimal skill) {
        return new GroupingCandidate(id, "球员" + id, id.intValue(), 1, skill);
    }
}
