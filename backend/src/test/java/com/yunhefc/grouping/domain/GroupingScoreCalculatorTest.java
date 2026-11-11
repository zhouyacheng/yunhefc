package com.yunhefc.grouping.domain;

import com.yunhefc.contracts.settings.GroupingSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingScoreCalculatorTest {

    @Test
    void zeroAppearancesForcesManualScore() {
        GroupingScore score = calculate(List.of(candidate(1L, 4)), Map.of(), 5).getFirst();

        assertThat(score.autoScore()).isNull();
        assertThat(score.finalScore()).isEqualByComparingTo("4.00");
        assertThat(score.manualOnly()).isTrue();
    }

    @Test
    void belowMinimumAppearancesKeepsAutoScoreButUsesManual() {
        GroupingScore score = calculate(List.of(candidate(1L, 2)),
                Map.of(1L, new GroupingPlayerStats(2, 3, 0)), 3).getFirst();

        assertThat(score.autoScore()).isNotNull();
        assertThat(score.finalScore()).isEqualByComparingTo("2.00");
        assertThat(score.manualOnly()).isTrue();
    }

    @Test
    void atMinimumAppearancesBlendsManualAndAuto() {
        GroupingScore score = calculate(List.of(candidate(1L, 2)),
                Map.of(1L, new GroupingPlayerStats(3, 0, 0)), 3).getFirst();

        // 出场率 3/3=1，进球/助攻全 0 归一为 0.5；auto=1+4*(0.5+0.125+0.125)=4.00。
        assertThat(score.autoScore()).isEqualByComparingTo("4.00");
        assertThat(score.finalScore()).isEqualByComparingTo("2.60");
        assertThat(score.manualOnly()).isFalse();
    }

    @Test
    void normalizesGoalsAgainstTheCandidatePoolMaximum() {
        List<GroupingScore> scores = calculate(
                List.of(candidate(1L, 3), candidate(2L, 3)),
                Map.of(1L, new GroupingPlayerStats(1, 4, 0),
                        2L, new GroupingPlayerStats(1, 2, 0)),
                1);

        // 进球归一 4/4=1、2/4=0.5；助攻全 0 取 0.5；出场率均为 1。
        assertThat(scores.get(0).autoScore()).isEqualByComparingTo("4.50");
        assertThat(scores.get(1).autoScore()).isEqualByComparingTo("4.00");
    }

    @Test
    void samePositiveStatNormalizesToOneAndAllZeroToHalf() {
        List<GroupingScore> samePositive = calculate(
                List.of(candidate(1L, 3), candidate(2L, 3)),
                Map.of(1L, new GroupingPlayerStats(1, 2, 0),
                        2L, new GroupingPlayerStats(1, 2, 0)),
                1);
        assertThat(samePositive.get(0).autoScore()).isEqualByComparingTo("4.50");

        List<GroupingScore> allZero = calculate(
                List.of(candidate(1L, 3)),
                Map.of(1L, new GroupingPlayerStats(1, 0, 0)),
                1);
        assertThat(allZero.getFirst().autoScore()).isEqualByComparingTo("4.00");
    }

    @Test
    void zeroFinishedMatchesUsesOneAsDenominator() {
        GroupingScore score = calculate(List.of(candidate(1L, 3)),
                Map.of(1L, new GroupingPlayerStats(1, 0, 0)), 0).getFirst();

        assertThat(score.autoScore()).isEqualByComparingTo("4.00");
    }

    @Test
    void appearanceRateIsCappedAtOne() {
        GroupingScore score = calculate(List.of(candidate(1L, 3)),
                Map.of(1L, new GroupingPlayerStats(5, 0, 0)), 2).getFirst();

        // 出场率 5/2 截断为 1；进球/助攻全 0 归一 0.5。
        assertThat(score.autoScore()).isEqualByComparingTo("4.00");
    }

    @Test
    void roundsManualToTwoDecimalsAndAutoAndFinalToTwoDecimalPlaces() {
        GroupingScore score = calculate(List.of(candidate(1L, 5)),
                Map.of(1L, new GroupingPlayerStats(3, 1, 0)), 4).getFirst();

        assertThat(score.manualScore().scale()).isEqualTo(1);
        assertThat(score.autoScore().scale()).isEqualTo(2);
        assertThat(score.finalScore().scale()).isEqualTo(2);
    }

    @Test
    void invalidWeightsFallBackAsASet() {
        GroupingWeights mainFallback = GroupingWeights.resolve(settings(70, 40, 50, 25, 25, 3));
        assertThat(mainFallback.manualWeight()).isEqualByComparingTo("70");
        assertThat(mainFallback.autoWeight()).isEqualByComparingTo("30");

        GroupingWeights negative = GroupingWeights.resolve(settings(-1, 101, 50, 25, 25, 3));
        assertThat(negative.manualWeight()).isEqualByComparingTo("70");

        GroupingWeights subFallback = GroupingWeights.resolve(settings(70, 30, 0, 0, 0, 3));
        assertThat(subFallback.appearanceWeight()).isEqualByComparingTo("50");
        assertThat(subFallback.goalWeight()).isEqualByComparingTo("25");
        assertThat(subFallback.assistWeight()).isEqualByComparingTo("25");

        assertThat(GroupingWeights.resolve(settings(70, 30, 50, 25, 25, -1)).minAppearances())
                .isEqualTo(3);
    }

    private static List<GroupingScore> calculate(List<GroupingCandidate> candidates,
                                                 Map<Long, GroupingPlayerStats> stats,
                                                 int finishedMatches) {
        return GroupingScoreCalculator.calculate(candidates, stats,
                GroupingWeights.resolve(settings(70, 30, 50, 25, 25, 3)), finishedMatches);
    }

    private static GroupingCandidate candidate(Long id, int skill) {
        return new GroupingCandidate(id, "球员" + id, id.intValue(), 1, skill);
    }

    private static GroupingSettings settings(int manual, int auto, int appearance,
                                             int goal, int assist, int minAppearances) {
        return new GroupingSettings(BigDecimal.valueOf(manual), BigDecimal.valueOf(auto),
                BigDecimal.valueOf(appearance), BigDecimal.valueOf(goal),
                BigDecimal.valueOf(assist), minAppearances);
    }
}
