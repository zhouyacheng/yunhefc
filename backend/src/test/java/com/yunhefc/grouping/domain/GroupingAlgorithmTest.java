package com.yunhefc.grouping.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingAlgorithmTest {

    @Test
    void assignsSnakeStartingWithTeamA() {
        List<AssignedPlayer> players = List.of(
                player(1L, 1, "3.00"), player(2L, 1, "3.00"),
                player(3L, 1, "3.00"), player(4L, 1, "3.00"));

        GroupingAssignment assignment = GroupingAlgorithm.assign(players, new FixedRandom(true, 0.5));

        assertThat(ids(assignment.teamA())).containsExactly(1L, 4L);
        assertThat(ids(assignment.teamB())).containsExactly(2L, 3L);
    }

    @Test
    void mirrorsSnakeWhenPositionStartsWithTeamB() {
        List<AssignedPlayer> players = List.of(
                player(1L, 1, "3.00"), player(2L, 1, "3.00"),
                player(3L, 1, "3.00"), player(4L, 1, "3.00"));

        GroupingAssignment assignment = GroupingAlgorithm.assign(players, new FixedRandom(false, 0.5));

        assertThat(ids(assignment.teamA())).containsExactly(2L, 3L);
        assertThat(ids(assignment.teamB())).containsExactly(1L, 4L);
    }

    @Test
    void consumesOneBooleanForEveryPositionEvenWhenEmpty() {
        CountingRandom random = new CountingRandom(false, 0.5);

        GroupingAlgorithm.assign(List.of(player(1L, 1, "3.00"), player(2L, 1, "3.00")), random);

        assertThat(random.booleanCalls).isEqualTo(4);
        assertThat(random.doubleCalls).isEqualTo(2);
    }

    @Test
    void sameSeedProducesSameAssignment() {
        List<AssignedPlayer> players = List.of(
                player(1L, 1, "3.00"), player(2L, 1, "3.00"),
                player(3L, 1, "3.00"), player(4L, 1, "3.00"));

        GroupingAssignment first = GroupingAlgorithm.assign(players, GroupingRandom.seeded(42));
        GroupingAssignment second = GroupingAlgorithm.assign(players, GroupingRandom.seeded(42));

        assertThat(ids(first.teamA())).isEqualTo(ids(second.teamA()));
        assertThat(ids(first.teamB())).isEqualTo(ids(second.teamB()));
    }

    @Test
    void differentSeedsDoNotAlwaysProduceTheSameTeams() {
        List<AssignedPlayer> players = List.of(
                player(1L, 1, "3.00"), player(2L, 1, "3.00"),
                player(3L, 1, "3.00"), player(4L, 1, "3.00"));
        Set<List<Long>> outcomes = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            outcomes.add(ids(GroupingAlgorithm.assign(players, GroupingRandom.seeded(seed)).teamA()));
        }

        assertThat(outcomes.size()).isGreaterThan(1);
    }

    @Test
    void countRepairMovesLowestOutfieldPlayer() {
        List<AssignedPlayer> teamA = new ArrayList<>(List.of(
                player(1L, 1, "3.00"), player(2L, 1, "2.00"), player(3L, 1, "1.00")));
        List<AssignedPlayer> teamB = new ArrayList<>(List.of(player(4L, 1, "5.00")));

        GroupingAlgorithm.repairCount(teamA, teamB);

        assertThat(ids(teamA)).containsExactly(1L, 2L);
        assertThat(ids(teamB)).containsExactly(4L, 3L);
    }

    @Test
    void goalkeeperRepairSwapsSmallestGoalkeeperForLowestOutfield() {
        List<AssignedPlayer> teamA = new ArrayList<>(List.of(
                player(1L, 0, "3.00"), player(2L, 0, "2.00"), player(3L, 1, "1.00")));
        List<AssignedPlayer> teamB = new ArrayList<>(List.of(
                player(4L, 1, "4.00"), player(5L, 1, "5.00")));

        GroupingAlgorithm.repairGoalkeeper(teamA, teamB, 2);

        assertThat(ids(teamA)).contains(2L, 3L, 4L);
        assertThat(ids(teamB)).contains(1L, 5L);
    }

    @Test
    void localSearchAcceptsOnlyStrictlyBetterSwaps() {
        List<AssignedPlayer> teamA = new ArrayList<>(List.of(
                player(1L, 1, "5.00"), player(2L, 1, "1.00")));
        List<AssignedPlayer> teamB = new ArrayList<>(List.of(
                player(3L, 1, "4.00"), player(4L, 1, "0.00")));

        GroupingAlgorithm.localSearch(teamA, teamB, 0);

        assertThat(ids(teamA)).containsExactly(2L, 3L);
        assertThat(ids(teamB)).containsExactly(1L, 4L);
    }

    @Test
    void localSearchLeavesBalancedEqualTeamsUntouched() {
        List<AssignedPlayer> teamA = new ArrayList<>(List.of(
                player(1L, 1, "3.00"), player(2L, 1, "3.00")));
        List<AssignedPlayer> teamB = new ArrayList<>(List.of(
                player(3L, 1, "3.00"), player(4L, 1, "3.00")));

        GroupingAlgorithm.localSearch(teamA, teamB, 0);

        assertThat(ids(teamA)).containsExactly(1L, 2L);
        assertThat(ids(teamB)).containsExactly(3L, 4L);
    }

    private static List<Long> ids(List<AssignedPlayer> team) {
        return team.stream().map(player -> player.candidate().playerId()).collect(Collectors.toList());
    }

    private static AssignedPlayer player(Long id, int position, String finalScore) {
        GroupingCandidate candidate = new GroupingCandidate(id, "球员" + id, id.intValue(), position, 3);
        GroupingScore score = new GroupingScore(id, BigDecimal.valueOf(3).setScale(1),
                null, new BigDecimal(finalScore), true);
        return new AssignedPlayer(candidate, score);
    }

    private static final class FixedRandom implements GroupingRandom {

        private final boolean booleanValue;
        private final double doubleValue;

        private FixedRandom(boolean booleanValue, double doubleValue) {
            this.booleanValue = booleanValue;
            this.doubleValue = doubleValue;
        }

        @Override
        public boolean nextBoolean() {
            return booleanValue;
        }

        @Override
        public double nextDouble() {
            return doubleValue;
        }
    }

    private static final class CountingRandom implements GroupingRandom {

        private final boolean booleanValue;
        private final double doubleValue;
        private int booleanCalls;
        private int doubleCalls;

        private CountingRandom(boolean booleanValue, double doubleValue) {
            this.booleanValue = booleanValue;
            this.doubleValue = doubleValue;
        }

        @Override
        public boolean nextBoolean() {
            booleanCalls++;
            return booleanValue;
        }

        @Override
        public double nextDouble() {
            doubleCalls++;
            return doubleValue;
        }
    }
}
