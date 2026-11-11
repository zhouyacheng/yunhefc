package com.yunhefc.grouping.domain;

import java.util.List;

/**
 * A/B 两队分组结果；两队均按 playerId 升序。
 */
public record GroupingAssignment(List<AssignedPlayer> teamA, List<AssignedPlayer> teamB) {

    public int candidateCount() {
        return teamA.size() + teamB.size();
    }
}
