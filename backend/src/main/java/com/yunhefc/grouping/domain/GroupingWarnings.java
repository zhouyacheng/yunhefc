package com.yunhefc.grouping.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 分组均衡提示；永不阻断。固定顺序：人数差 → 门将 → 各外场位置。
 */
public final class GroupingWarnings {

    private static final int GOALKEEPER = 0;
    private static final String[] OUTFIELD_LABELS = {"", "后卫", "中场", "前锋"};

    private GroupingWarnings() {
    }

    public static List<String> compute(List<AssignedPlayer> teamA,
                                       List<AssignedPlayer> teamB,
                                       List<GroupingCandidate> candidates) {
        Set<String> warnings = new LinkedHashSet<>();
        int countDiff = Math.abs(teamA.size() - teamB.size());
        if (countDiff > 1) {
            warnings.add("两队人数差 " + countDiff + " 人，可能影响均衡");
        }

        int goalkeeperPool = count(candidates, GOALKEEPER);
        long teamGoalkeepersA = countTeam(teamA, GOALKEEPER);
        long teamGoalkeepersB = countTeam(teamB, GOALKEEPER);
        if (goalkeeperPool == 0) {
            warnings.add("名单中没有门将");
        } else if (goalkeeperPool == 1) {
            warnings.add("名单中仅 1 名门将；0/1 名只提示不阻断");
        } else if (teamGoalkeepersA == 0 || teamGoalkeepersB == 0) {
            warnings.add("有球队缺少门将");
        }

        for (int position = 1; position <= 3; position++) {
            String label = OUTFIELD_LABELS[position];
            int poolCount = count(candidates, position);
            if (poolCount < 2) {
                warnings.add(label + "位置仅 " + poolCount + " 人，位置均衡可能受限");
            }
            int positionDiff = Math.abs((int) countTeam(teamA, position)
                    - (int) countTeam(teamB, position));
            if (positionDiff > 1) {
                warnings.add(label + "人数差 " + positionDiff);
            }
        }
        return List.copyOf(warnings);
    }

    private static int count(List<GroupingCandidate> candidates, int position) {
        return (int) candidates.stream()
                .filter(candidate -> candidate.position() == position)
                .count();
    }

    private static long countTeam(List<AssignedPlayer> team, int position) {
        return team.stream()
                .filter(player -> player.candidate().position() == position)
                .count();
    }
}
