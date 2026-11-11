package com.yunhefc.grouping.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 逐位置蛇形分配 + 人数修平 + 门将修复 + 局部搜索。
 */
public final class GroupingAlgorithm {

    private static final int GOALKEEPER = 0;
    private static final int MIN_ROUNDS = 20;
    private static final double NOISE_RANGE = 1.2;

    private static final Comparator<AssignedPlayer> LOWEST_FINAL = Comparator
            .comparing((AssignedPlayer player) -> player.score().finalScore())
            .thenComparing(player -> player.candidate().playerId());

    private GroupingAlgorithm() {
    }

    public static GroupingAssignment assign(List<AssignedPlayer> players, GroupingRandom random) {
        List<AssignedPlayer> sorted = sortedById(players);
        List<AssignedPlayer> teamA = new ArrayList<>();
        List<AssignedPlayer> teamB = new ArrayList<>();

        for (int position = 0; position <= 3; position++) {
            // 空位置也消耗一次随机数，保证固定种子下抽取顺序稳定。
            boolean startWithTeamA = random.nextBoolean();
            int currentPosition = position;
            List<AssignedPlayer> group = sorted.stream()
                    .filter(player -> player.candidate().position() == currentPosition)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }
            Map<Long, Double> noise = new HashMap<>();
            for (AssignedPlayer player : group) {
                noise.put(player.candidate().playerId(), random.nextDouble());
            }
            List<AssignedPlayer> ordered = new ArrayList<>(group);
            ordered.sort(withinPositionOrder(noise));
            for (int index = 0; index < ordered.size(); index++) {
                boolean startsSlot = index % 4 == 0 || index % 4 == 3;
                boolean toTeamA = startWithTeamA == startsSlot;
                (toTeamA ? teamA : teamB).add(ordered.get(index));
            }
        }

        int goalkeeperPool = (int) sorted.stream()
                .filter(player -> player.candidate().position() == GOALKEEPER)
                .count();
        repairCount(teamA, teamB);
        repairGoalkeeper(teamA, teamB, goalkeeperPool);
        localSearch(teamA, teamB, goalkeeperPool);
        return new GroupingAssignment(sortedById(teamA), sortedById(teamB));
    }

    private static Comparator<AssignedPlayer> withinPositionOrder(Map<Long, Double> noise) {
        return (left, right) -> {
            int byScore = Double.compare(
                    effectiveScore(right, noise.get(right.candidate().playerId())),
                    effectiveScore(left, noise.get(left.candidate().playerId())));
            if (byScore != 0) {
                return byScore;
            }
            int byJersey = Comparator.nullsLast(Integer::compare)
                    .compare(left.candidate().jersey(), right.candidate().jersey());
            if (byJersey != 0) {
                return byJersey;
            }
            return Long.compare(left.candidate().playerId(), right.candidate().playerId());
        };
    }

    private static double effectiveScore(AssignedPlayer player, double noise) {
        return player.score().finalScore().doubleValue() + (noise - 0.5) * NOISE_RANGE;
    }

    static void repairCount(List<AssignedPlayer> teamA, List<AssignedPlayer> teamB) {
        while (Math.abs(teamA.size() - teamB.size()) > 1) {
            List<AssignedPlayer> larger = teamA.size() > teamB.size() ? teamA : teamB;
            List<AssignedPlayer> smaller = larger == teamA ? teamB : teamA;
            AssignedPlayer moved = larger.stream()
                    .filter(player -> player.candidate().position() != GOALKEEPER)
                    .min(LOWEST_FINAL)
                    .orElseGet(() -> larger.stream().min(LOWEST_FINAL).orElse(null));
            if (moved == null) {
                return;
            }
            larger.remove(moved);
            smaller.add(moved);
        }
    }

    static void repairGoalkeeper(List<AssignedPlayer> teamA,
                                 List<AssignedPlayer> teamB,
                                 int goalkeeperPool) {
        if (goalkeeperPool < 2) {
            return;
        }
        long goalkeepersA = countPosition(teamA, GOALKEEPER);
        long goalkeepersB = countPosition(teamB, GOALKEEPER);
        if (goalkeepersA == 0 && goalkeepersB > 0) {
            swapOneGoalkeeper(teamB, teamA);
        } else if (goalkeepersB == 0 && goalkeepersA > 0) {
            swapOneGoalkeeper(teamA, teamB);
        }
    }

    private static void swapOneGoalkeeper(List<AssignedPlayer> donor, List<AssignedPlayer> receiver) {
        AssignedPlayer goalkeeper = donor.stream()
                .filter(player -> player.candidate().position() == GOALKEEPER)
                .min(Comparator.comparing(player -> player.candidate().playerId()))
                .orElse(null);
        AssignedPlayer outfield = receiver.stream()
                .filter(player -> player.candidate().position() != GOALKEEPER)
                .min(LOWEST_FINAL)
                .orElse(null);
        if (goalkeeper == null || outfield == null) {
            return;
        }
        donor.remove(goalkeeper);
        receiver.remove(outfield);
        donor.add(outfield);
        receiver.add(goalkeeper);
    }

    static void localSearch(List<AssignedPlayer> teamA,
                            List<AssignedPlayer> teamB,
                            int goalkeeperPool) {
        sortById(teamA);
        sortById(teamB);
        int rounds = Math.max(MIN_ROUNDS, (teamA.size() + teamB.size()) * (teamA.size() + teamB.size()));
        GroupingObjective current = GroupingObjective.evaluate(teamA, teamB, goalkeeperPool);
        for (int round = 0; round < rounds; round++) {
            GroupingObjective best = current;
            int bestA = -1;
            int bestB = -1;
            for (int i = 0; i < teamA.size(); i++) {
                for (int j = 0; j < teamB.size(); j++) {
                    AssignedPlayer fromA = teamA.get(i);
                    AssignedPlayer fromB = teamB.get(j);
                    teamA.set(i, fromB);
                    teamB.set(j, fromA);
                    GroupingObjective candidate = GroupingObjective.evaluate(teamA, teamB, goalkeeperPool);
                    teamA.set(i, fromA);
                    teamB.set(j, fromB);
                    if (candidate.compareTo(best) < 0) {
                        best = candidate;
                        bestA = i;
                        bestB = j;
                    }
                }
            }
            if (bestA < 0) {
                return;
            }
            AssignedPlayer fromA = teamA.get(bestA);
            teamA.set(bestA, teamB.get(bestB));
            teamB.set(bestB, fromA);
            current = best;
            sortById(teamA);
            sortById(teamB);
        }
    }

    private static long countPosition(List<AssignedPlayer> team, int position) {
        return team.stream()
                .filter(player -> player.candidate().position() == position)
                .count();
    }

    private static List<AssignedPlayer> sortedById(List<AssignedPlayer> players) {
        return players.stream()
                .sorted(Comparator.comparing(player -> player.candidate().playerId()))
                .toList();
    }

    private static void sortById(List<AssignedPlayer> players) {
        players.sort(Comparator.comparing(player -> player.candidate().playerId()));
    }
}
