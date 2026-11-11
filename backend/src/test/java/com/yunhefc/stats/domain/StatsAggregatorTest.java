package com.yunhefc.stats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.contracts.attendance.AttendanceSnapshot;
import com.yunhefc.contracts.event.EventStatsSnapshot;
import com.yunhefc.contracts.match.MatchSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

class StatsAggregatorTest {

    private static final Long TEAM_ID = 1L;
    private static final Long MATCH_ID = 10L;
    private static final Instant START = Instant.parse("2026-03-01T04:00:00Z");

    @Test
    void countsPresentAndTempPresentAsAppearancesButNotAbsent() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, 4)),
                List.of(attendance(MATCH_ID, 1L, 0), attendance(MATCH_ID, 2L, 2),
                        attendance(MATCH_ID, 3L, 1)),
                List.of());

        assertThat(tallies.get(1L).appearances()).isEqualTo(1);
        assertThat(tallies.get(2L).appearances()).isEqualTo(1);
        assertThat(tallies).doesNotContainKey(3L);
    }

    @Test
    void countsGoalsAndAssistsButNotOwnGoals() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, 4)),
                List.of(),
                List.of(event(MATCH_ID, 0, 1L, 2L), event(MATCH_ID, 1, 1L, null),
                        event(MATCH_ID, 0, 3L, null)));

        assertThat(tallies.get(1L).goals()).isEqualTo(1);
        assertThat(tallies.get(1L).assists()).isZero();
        assertThat(tallies.get(2L).assists()).isEqualTo(1);
        assertThat(tallies.get(3L).goals()).isEqualTo(1);
    }

    @Test
    void ignoresCancelledMatches() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, 6), match(11L, 4)),
                List.of(attendance(MATCH_ID, 1L, 0), attendance(11L, 1L, 0)),
                List.of(event(MATCH_ID, 0, 1L, 2L), event(11L, 0, 1L, null)));

        assertThat(tallies.get(1L).appearances()).isEqualTo(1);
        assertThat(tallies.get(1L).goals()).isEqualTo(1);
        assertThat(tallies).doesNotContainKey(2L);
    }

    @Test
    void ignoresRecordsForMatchesOutsideWindow() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, 4)),
                List.of(attendance(99L, 1L, 0)),
                List.of(event(99L, 0, 1L, null)));

        assertThat(tallies).isEmpty();
    }

    private static MatchSnapshot match(Long id, int status) {
        return new MatchSnapshot(id, TEAM_ID, "统计测试", START, START.plusSeconds(7_200),
                START.minusSeconds(86_400), START.minusSeconds(3_600), 30, 4, 2, "场地",
                status, "橙队", "兰队", null, "2026", 1, null, true);
    }

    private static AttendanceSnapshot attendance(Long matchId, Long playerId, int status) {
        return new AttendanceSnapshot(1L, matchId, playerId, status, null, 0, null, START);
    }

    private static EventStatsSnapshot event(Long matchId, int type, Long playerId, Long assistId) {
        return new EventStatsSnapshot(matchId, type, playerId, assistId);
    }
}
