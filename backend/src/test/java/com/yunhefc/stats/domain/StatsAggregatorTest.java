package com.yunhefc.stats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.contracts.attendance.AttendanceSnapshot;
import com.yunhefc.contracts.event.EventStatsSnapshot;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import com.yunhefc.contracts.match.MatchSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class StatsAggregatorTest {

    private static final Long TEAM_ID = 1L;
    private static final Long MATCH_ID = 10L;
    private static final LocalDateTime START = ShanghaiTime.parseLenient("2026-03-01T04:00:00Z");

    private static final int INTERNAL = 1;
    private static final int TRAINING = 2;
    private static final int OFFICIAL = 0;
    private static final int IN_PROGRESS = 3;
    private static final int FINISHED = 4;
    private static final int POSTPONED = 5;

    @Test
    void countsPresentAndTempPresentAsAppearancesButNotAbsent() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, FINISHED)),
                List.of(attendance(MATCH_ID, 1L, 0), attendance(MATCH_ID, 2L, 2),
                        attendance(MATCH_ID, 3L, 1)),
                List.of(), Map.of(), Map.of());

        assertThat(tallies.get(1L).appearances()).isEqualTo(1);
        assertThat(tallies.get(2L).appearances()).isEqualTo(1);
        assertThat(tallies).doesNotContainKey(3L);
    }

    @Test
    void countsGoalsAndAssistsButNotOwnGoals() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, FINISHED)),
                List.of(),
                List.of(event(MATCH_ID, 0, 1L, 2L), event(MATCH_ID, 1, 1L, null),
                        event(MATCH_ID, 0, 3L, null)),
                Map.of(), Map.of());

        assertThat(tallies.get(1L).goals()).isEqualTo(1);
        assertThat(tallies.get(1L).assists()).isZero();
        assertThat(tallies.get(2L).assists()).isEqualTo(1);
        assertThat(tallies.get(3L).goals()).isEqualTo(1);
    }

    @Test
    void countsOwnGoalsSeparatelyWithoutReducingGoals() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, FINISHED)),
                List.of(),
                List.of(event(MATCH_ID, 1, 1L, null)),
                Map.of(), Map.of());

        assertThat(tallies.get(1L).ownGoals()).isEqualTo(1);
        assertThat(tallies.get(1L).goals()).isZero();
    }

    @Test
    void ignoresCancelledMatchesIncludingOwnGoalsAndWinRate() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, 6), match(11L, FINISHED)),
                List.of(attendance(MATCH_ID, 1L, 0), attendance(11L, 1L, 0)),
                List.of(event(MATCH_ID, 0, 1L, 2L), event(MATCH_ID, 1, 1L, null),
                        event(11L, 0, 1L, null)),
                Map.of(MATCH_ID, grouping(MATCH_ID, Map.of(1L, 0))),
                Map.of(MATCH_ID, score(3, 0)));

        assertThat(tallies.get(1L).appearances()).isEqualTo(1);
        assertThat(tallies.get(1L).goals()).isEqualTo(1);
        assertThat(tallies.get(1L).ownGoals()).isZero();
        assertThat(tallies.get(1L).internalAppearances()).isZero();
        assertThat(tallies.get(1L).internalWins()).isZero();
        assertThat(tallies).doesNotContainKey(2L);
    }

    @Test
    void ignoresRecordsForMatchesOutsideWindow() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, FINISHED)),
                List.of(attendance(99L, 1L, 0)),
                List.of(event(99L, 0, 1L, null)),
                Map.of(), Map.of());

        assertThat(tallies).isEmpty();
    }

    @Test
    void countsInternalAppearancesAndWinsBySavedGroupingSide() {
        Long homeWin = 11L;
        Long awayWin = 12L;
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(homeWin, FINISHED), match(awayWin, FINISHED)),
                List.of(attendance(homeWin, 1L, 0), attendance(homeWin, 2L, 2),
                        attendance(homeWin, 3L, 1), attendance(homeWin, 4L, 2),
                        attendance(awayWin, 1L, 0), attendance(awayWin, 2L, 0)),
                List.of(),
                Map.of(homeWin, grouping(homeWin, Map.of(1L, 0, 2L, 1, 3L, 0)),
                        awayWin, grouping(awayWin, Map.of(1L, 0, 2L, 1))),
                Map.of(homeWin, score(2, 1), awayWin, score(0, 1)));

        assertThat(tallies.get(1L).internalAppearances()).isEqualTo(2);
        assertThat(tallies.get(1L).internalWins()).isEqualTo(1);
        assertThat(tallies.get(2L).internalAppearances()).isEqualTo(2);
        assertThat(tallies.get(2L).internalWins()).isEqualTo(1);
        // 缺勤球员即使已分组也不计入胜率分母。
        assertThat(tallies).doesNotContainKey(3L);
        // 临时到场球员不在分组内：计入分母，但无归属不计胜场。
        assertThat(tallies.get(4L).internalAppearances()).isEqualTo(1);
        assertThat(tallies.get(4L).internalWins()).isZero();
    }

    @Test
    void drawCountsInDenominatorWithoutWin() {
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(MATCH_ID, FINISHED)),
                List.of(attendance(MATCH_ID, 1L, 0)),
                List.of(),
                Map.of(MATCH_ID, grouping(MATCH_ID, Map.of(1L, 0))),
                Map.of(MATCH_ID, score(1, 1)));

        assertThat(tallies.get(1L).internalAppearances()).isEqualTo(1);
        assertThat(tallies.get(1L).internalWins()).isZero();
    }

    @Test
    void skipsInternalMatchesWithoutGroupingKnownScoreOrFinishedStatus() {
        Long ungrouped = 11L;
        Long unknownScore = 12L;
        Long inProgress = 13L;
        Long training = 14L;
        Long official = 15L;
        Long postponed = 16L;
        Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                List.of(match(ungrouped, FINISHED), match(unknownScore, FINISHED),
                        match(inProgress, IN_PROGRESS), match(training, FINISHED, TRAINING),
                        match(official, FINISHED, OFFICIAL), match(postponed, POSTPONED)),
                List.of(attendance(ungrouped, 1L, 0), attendance(unknownScore, 1L, 0),
                        attendance(inProgress, 1L, 0), attendance(training, 1L, 0),
                        attendance(official, 1L, 0), attendance(postponed, 1L, 0)),
                List.of(),
                Map.of(unknownScore, grouping(unknownScore, Map.of(1L, 0)),
                        inProgress, grouping(inProgress, Map.of(1L, 0)),
                        training, grouping(training, Map.of(1L, 0)),
                        official, grouping(official, Map.of(1L, 0)),
                        postponed, grouping(postponed, Map.of(1L, 0))),
                Map.of(ungrouped, score(3, 0), inProgress, score(3, 0),
                        training, score(3, 0), official, score(3, 0), postponed, score(3, 0),
                        unknownScore, new InternalMatchScore(null, null)));

        assertThat(tallies.get(1L).appearances()).isEqualTo(6);
        assertThat(tallies.get(1L).internalAppearances()).isZero();
        assertThat(tallies.get(1L).internalWins()).isZero();
    }

    private static MatchSnapshot match(Long id, int status) {
        return match(id, status, INTERNAL);
    }

    private static MatchSnapshot match(Long id, int status, int type) {
        return new MatchSnapshot(id, TEAM_ID, "统计测试", START, START.plusSeconds(7_200),
                START.minusSeconds(86_400), START.minusSeconds(3_600), 30, 4, 2, "场地",
                status, "橙队", "兰队", null, "2026", type, null, true, null, null);
    }

    private static AttendanceSnapshot attendance(Long matchId, Long playerId, int status) {
        return new AttendanceSnapshot(1L, matchId, playerId, status, null, 0, null, START);
    }

    private static EventStatsSnapshot event(Long matchId, int type, Long playerId, Long assistId) {
        return new EventStatsSnapshot(matchId, type, playerId, assistId);
    }

    private static SavedGroupingSnapshot grouping(Long matchId, Map<Long, Integer> playerTeams) {
        return new SavedGroupingSnapshot(matchId, START, playerTeams);
    }

    private static InternalMatchScore score(int home, int away) {
        return new InternalMatchScore(home, away);
    }
}
