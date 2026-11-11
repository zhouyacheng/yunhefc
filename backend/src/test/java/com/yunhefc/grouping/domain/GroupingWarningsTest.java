package com.yunhefc.grouping.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingWarningsTest {

    @Test
    void warnsInFixedOrder() {
        List<GroupingCandidate> candidates = List.of(
                candidate(1L, 1), candidate(2L, 1),
                candidate(3L, 2), candidate(4L, 2),
                candidate(5L, 3));
        List<AssignedPlayer> teamA = List.of(player(1L, 1), player(3L, 2), player(5L, 3));
        List<AssignedPlayer> teamB = List.of(player(2L, 1), player(4L, 2));

        // 人为构造 A=3/B=2 不触发人数差；补一个 4:1 场景验证顺序。
        assertThat(GroupingWarnings.compute(teamA, teamB, candidates))
                .containsExactly("名单中没有门将", "前锋位置仅 1 人，位置均衡可能受限");

        List<AssignedPlayer> paddedA = List.of(player(1L, 1), player(2L, 1),
                player(3L, 2), player(5L, 3));
        List<AssignedPlayer> paddedB = List.of(player(4L, 2));
        assertThat(GroupingWarnings.compute(paddedA, paddedB, candidates))
                .containsExactly(
                        "两队人数差 3 人，可能影响均衡",
                        "名单中没有门将",
                        "后卫人数差 2",
                        "前锋位置仅 1 人，位置均衡可能受限");
    }

    @Test
    void reportsSingleOrMissingTeamGoalkeeper() {
        List<GroupingCandidate> oneGoalkeeper = List.of(candidate(1L, 0), candidate(2L, 1));
        assertThat(GroupingWarnings.compute(
                List.of(player(1L, 0), player(2L, 1)), List.of(), oneGoalkeeper))
                .contains("名单中仅 1 名门将；0/1 名只提示不阻断");

        List<GroupingCandidate> twoGoalkeepers = List.of(candidate(1L, 0), candidate(2L, 0));
        assertThat(GroupingWarnings.compute(
                List.of(player(1L, 0), player(2L, 0)), List.of(), twoGoalkeepers))
                .contains("有球队缺少门将");
    }

    @Test
    void reportsPositionCountDifference() {
        List<GroupingCandidate> candidates = List.of(
                candidate(1L, 1), candidate(2L, 1), candidate(3L, 1));
        assertThat(GroupingWarnings.compute(
                List.of(player(1L, 1), player(2L, 1)), List.of(), candidates))
                .contains("后卫人数差 2");
    }

    private static GroupingCandidate candidate(Long id, int position) {
        return new GroupingCandidate(id, "球员" + id, id.intValue(), position, 3);
    }

    private static AssignedPlayer player(Long id, int position) {
        GroupingCandidate candidate = candidate(id, position);
        GroupingScore score = new GroupingScore(id, BigDecimal.valueOf(3).setScale(1), null,
                BigDecimal.valueOf(3).setScale(2), true);
        return new AssignedPlayer(candidate, score);
    }
}
