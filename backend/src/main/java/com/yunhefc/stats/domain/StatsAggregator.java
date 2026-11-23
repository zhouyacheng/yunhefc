package com.yunhefc.stats.domain;

import com.yunhefc.contracts.attendance.AttendanceSnapshot;
import com.yunhefc.contracts.event.EventStatsSnapshot;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import com.yunhefc.contracts.match.MatchSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 统计口径：出场=正常/临时到场；进球=GOAL；助攻=GOAL 的助攻人；乌龙=OWN_GOAL 单列；
 * 胜率只统计已保存分组、已结束、比分已知的队内赛，取消比赛不计任何统计。
 */
public final class StatsAggregator {

    private StatsAggregator() {
    }

    public static Map<Long, PlayerTally> tally(Collection<MatchSnapshot> matches,
                                               Collection<AttendanceSnapshot> attendance,
                                               Collection<EventStatsSnapshot> events,
                                               Map<Long, SavedGroupingSnapshot> groupings,
                                               Map<Long, InternalMatchScore> internalScores) {
        Set<Long> eligibleMatches = new HashSet<>();
        for (MatchSnapshot match : matches) {
            if (!match.cancelled()) {
                eligibleMatches.add(match.id());
            }
        }

        Map<Long, MutableTally> tallies = new HashMap<>();
        Map<Long, Set<Long>> appearedByMatch = new HashMap<>();
        for (AttendanceSnapshot record : attendance) {
            if (eligibleMatches.contains(record.matchId())
                    && record.playerId() != null
                    && record.countsAsAppearance()) {
                tally(tallies, record.playerId()).appearances++;
                appearedByMatch.computeIfAbsent(record.matchId(), ignored -> new HashSet<>())
                        .add(record.playerId());
            }
        }
        for (EventStatsSnapshot event : events) {
            if (!eligibleMatches.contains(event.matchId())) {
                continue;
            }
            if (event.personalGoal()) {
                tally(tallies, event.playerId()).goals++;
                if (event.assistPlayerId() != null) {
                    tally(tallies, event.assistPlayerId()).assists++;
                }
            }
            if (event.ownGoal()) {
                tally(tallies, event.playerId()).ownGoals++;
            }
        }
        for (MatchSnapshot match : matches) {
            if (!match.internal() || !match.finished()) {
                continue;
            }
            SavedGroupingSnapshot grouping = groupings.get(match.id());
            InternalMatchScore score = internalScores.get(match.id());
            if (grouping == null || score == null || !score.known()) {
                continue;
            }
            Set<Long> appeared = appearedByMatch.getOrDefault(match.id(), Set.of());
            // 分母只要求场次有分组且球员计入出场；临时到场球员可能不在分组内，仍计入分母。
            appeared.forEach(playerId -> tally(tallies, playerId).internalAppearances++);
            // 胜场只能按已保存分组判定一方；不在分组内的球员无归属，不计胜场。
            grouping.playerTeams().forEach((playerId, team) -> {
                if (appeared.contains(playerId) && score.teamWon(team)) {
                    tally(tallies, playerId).internalWins++;
                }
            });
        }

        Map<Long, PlayerTally> result = new HashMap<>();
        tallies.forEach((playerId, value) -> result.put(playerId, value.toTally()));
        return result;
    }

    private static MutableTally tally(Map<Long, MutableTally> tallies, Long playerId) {
        return tallies.computeIfAbsent(playerId, ignored -> new MutableTally());
    }

    private static final class MutableTally {
        private int appearances;
        private int goals;
        private int assists;
        private int ownGoals;
        private int internalAppearances;
        private int internalWins;

        private PlayerTally toTally() {
            return new PlayerTally(appearances, goals, assists,
                    ownGoals, internalAppearances, internalWins);
        }
    }
}
