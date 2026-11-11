package com.yunhefc.stats.domain;

import com.yunhefc.contracts.attendance.AttendanceSnapshot;
import com.yunhefc.contracts.event.EventStatsSnapshot;
import com.yunhefc.contracts.match.MatchSnapshot;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 统计口径：出场=正常/临时到场；进球=GOAL；助攻=GOAL 的助攻人；取消比赛不计任何统计。
 */
public final class StatsAggregator {

    private StatsAggregator() {
    }

    public static Map<Long, PlayerTally> tally(Collection<MatchSnapshot> matches,
                                               Collection<AttendanceSnapshot> attendance,
                                               Collection<EventStatsSnapshot> events) {
        Set<Long> eligibleMatches = new HashSet<>();
        for (MatchSnapshot match : matches) {
            if (!match.cancelled()) {
                eligibleMatches.add(match.id());
            }
        }

        Map<Long, MutableTally> tallies = new HashMap<>();
        for (AttendanceSnapshot record : attendance) {
            if (eligibleMatches.contains(record.matchId())
                    && record.playerId() != null
                    && record.countsAsAppearance()) {
                tally(tallies, record.playerId()).appearances++;
            }
        }
        for (EventStatsSnapshot event : events) {
            if (!eligibleMatches.contains(event.matchId()) || !event.personalGoal()) {
                continue;
            }
            tally(tallies, event.playerId()).goals++;
            if (event.assistPlayerId() != null) {
                tally(tallies, event.assistPlayerId()).assists++;
            }
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

        private PlayerTally toTally() {
            return new PlayerTally(appearances, goals, assists);
        }
    }
}
