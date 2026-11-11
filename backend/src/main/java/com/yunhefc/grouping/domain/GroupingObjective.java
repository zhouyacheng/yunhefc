package com.yunhefc.grouping.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分组目标元组，按字典序最小化：缺门将 → 人数差 >1 → 总分差 → 外场位置差。
 */
public record GroupingObjective(
        boolean missingGoalkeeper,
        boolean countGap,
        BigDecimal scoreGap,
        int positionGap) implements Comparable<GroupingObjective> {

    private static final int GOALKEEPER = 0;

    public static GroupingObjective evaluate(List<AssignedPlayer> teamA,
                                             List<AssignedPlayer> teamB,
                                             int goalkeeperPool) {
        long teamGoalkeepersA = countPosition(teamA, GOALKEEPER);
        long teamGoalkeepersB = countPosition(teamB, GOALKEEPER);
        boolean missingGoalkeeper = goalkeeperPool >= 2
                && (teamGoalkeepersA == 0 || teamGoalkeepersB == 0);
        boolean countGap = Math.abs(teamA.size() - teamB.size()) > 1;
        BigDecimal scoreGap = sumFinal(teamA).subtract(sumFinal(teamB)).abs();
        int positionGap = 0;
        for (int position = 1; position <= 3; position++) {
            positionGap += Math.abs(countPosition(teamA, position) - countPosition(teamB, position));
        }
        return new GroupingObjective(missingGoalkeeper, countGap, scoreGap, positionGap);
    }

    public static BigDecimal sumFinal(List<AssignedPlayer> team) {
        return team.stream()
                .map(player -> player.score().finalScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long countPosition(List<AssignedPlayer> team, int position) {
        return team.stream()
                .filter(player -> player.candidate().position() == position)
                .count();
    }

    @Override
    public int compareTo(GroupingObjective other) {
        int byGoalkeeper = Boolean.compare(missingGoalkeeper, other.missingGoalkeeper);
        if (byGoalkeeper != 0) {
            return byGoalkeeper;
        }
        int byCount = Boolean.compare(countGap, other.countGap);
        if (byCount != 0) {
            return byCount;
        }
        int byScore = scoreGap.compareTo(other.scoreGap);
        if (byScore != 0) {
            return byScore;
        }
        return Integer.compare(positionGap, other.positionGap);
    }
}
