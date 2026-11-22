package com.yunhefc.stats.application;

import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.contracts.attendance.AttendanceReadPort;
import com.yunhefc.contracts.attendance.AttendanceSnapshot;
import com.yunhefc.contracts.event.EventStatsReadPort;
import com.yunhefc.contracts.event.EventStatsSnapshot;
import com.yunhefc.contracts.grouping.GroupingReadPort;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.match.MatchSummaryReadPort;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.stats.domain.InternalMatchScore;
import com.yunhefc.stats.domain.PlayerStatsSummary;
import com.yunhefc.stats.domain.PlayerTally;
import com.yunhefc.stats.domain.StatsAggregator;
import com.yunhefc.stats.infrastructure.StatsCache;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 球队全部赛季全量重算：逐赛季覆盖写汇总表并刷新 Redis 缓存。
 */
@Service
@RequiredArgsConstructor
public class StatsRollupApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StatsRollupApplicationService.class);

    private final MatchReadPort matchReadPort;
    private final AttendanceReadPort attendanceReadPort;
    private final EventStatsReadPort eventStatsReadPort;
    private final GroupingReadPort groupingReadPort;
    private final MatchSummaryReadPort matchSummaryReadPort;
    private final PlayerReadPort playerReadPort;
    private final TeamReadPort teamReadPort;
    private final StatsSummaryReader summaryReader;
    private final StatsSummaryWriter summaryWriter;
    private final StatsCache statsCache;
    private final StatsQueryService statsQueryService;

    /**
     * 按有效赛季分组重算。单赛季写入失败只保留该赛季旧值，不影响其他赛季。
     */
    public List<RollupResult> rollUpAllSeasons() {
        Long teamId = teamReadPort.currentTeamId();
        List<MatchSnapshot> matches = matchReadPort.findRollupMatches(teamId);
        Map<String, List<MatchSnapshot>> matchesBySeason = matches.stream()
                .filter(match -> match.effectiveSeason() != null)
                .collect(Collectors.groupingBy(MatchSnapshot::effectiveSeason));

        // 汇总表已有赛季用于把比赛删除/改赛季后的残留行归零；当前年份保证无比赛也有缓存。
        Set<String> seasons = new TreeSet<>(Comparator.reverseOrder());
        seasons.addAll(matchesBySeason.keySet());
        seasons.addAll(summaryReader.seasons(teamId));
        seasons.add(statsQueryService.currentSeason());

        List<Long> matchIds = matches.stream().map(MatchSnapshot::id).toList();
        List<AttendanceSnapshot> attendance = attendanceReadPort.findByMatchIds(matchIds);
        List<EventStatsSnapshot> events = eventStatsReadPort.findByMatchIds(matchIds);
        List<Long> internalMatchIds = matches.stream()
                .filter(MatchSnapshot::internal)
                .filter(MatchSnapshot::finished)
                .map(MatchSnapshot::id)
                .toList();
        Map<Long, SavedGroupingSnapshot> groupings = savedGroupings(internalMatchIds);
        Map<Long, InternalMatchScore> internalScores = internalScores(internalMatchIds, teamId);

        Map<String, Map<Long, PlayerTally>> talliesBySeason = new LinkedHashMap<>();
        Map<String, Set<Long>> existingBySeason = new LinkedHashMap<>();
        Set<Long> candidates = new HashSet<>();
        for (String season : seasons) {
            Map<Long, PlayerTally> tallies = StatsAggregator.tally(
                    matchesBySeason.getOrDefault(season, List.of()), attendance, events,
                    groupings, internalScores);
            Set<Long> existing = new HashSet<>(summaryReader.playerIds(teamId, season));
            talliesBySeason.put(season, tallies);
            existingBySeason.put(season, existing);
            candidates.addAll(tallies.keySet());
            candidates.addAll(existing);
        }

        // 只覆盖本队现存球员；已迁移/已删除球员的历史行保留原值。
        Set<Long> teamPlayerIds = playerReadPort.findByIds(teamId, candidates).stream()
                .map(PlayerSnapshot::id)
                .collect(Collectors.toSet());

        LocalDateTime now = ShanghaiTime.now();
        List<RollupResult> results = new ArrayList<>();
        for (String season : seasons) {
            Map<Long, PlayerTally> tallies = talliesBySeason.get(season);
            Set<Long> candidateIds = new HashSet<>(tallies.keySet());
            candidateIds.addAll(existingBySeason.get(season));
            List<PlayerStatsSummary> summaries = candidateIds.stream()
                    .filter(teamPlayerIds::contains)
                    .sorted()
                    .map(playerId -> summary(teamId, season, playerId,
                            tallies.getOrDefault(playerId, PlayerTally.ZERO)))
                    .toList();
            try {
                summaryWriter.replace(summaries, now);
            } catch (RuntimeException ex) {
                log.error("stats rollup failed, season={}, previous summary retained", season, ex);
                continue;
            }
            // 缓存只在汇总表事务提交后刷新，避免回滚后缓存领先于数据库。
            List<PlayerStatsRow> rows = summaries.stream()
                    .map(summary -> PlayerStatsRow.of(summary, now))
                    .toList();
            statsCache.write(teamId, season, rows);
            results.add(new RollupResult(teamId, season,
                    matchesBySeason.getOrDefault(season, List.of()).size(), rows.size()));
        }
        return results;
    }

    /**
     * 只读取已结束队内赛的分组；其他类型不参与胜率。
     */
    private Map<Long, SavedGroupingSnapshot> savedGroupings(List<Long> internalMatchIds) {
        return internalMatchIds.isEmpty()
                ? Map.of()
                : groupingReadPort.findSavedByMatchIds(internalMatchIds);
    }

    /**
     * 只读取已结束队内赛的最终比分；比分未知的场次由聚合器跳过。
     */
    private Map<Long, InternalMatchScore> internalScores(List<Long> internalMatchIds, Long teamId) {
        if (internalMatchIds.isEmpty()) {
            return Map.of();
        }
        return matchSummaryReadPort.findSummaries(internalMatchIds, teamId).values().stream()
                .collect(Collectors.toMap(MatchSummarySnapshot::id,
                        summary -> new InternalMatchScore(summary.scoreHome(), summary.scoreAway())));
    }

    private static PlayerStatsSummary summary(Long teamId, String season, Long playerId,
                                              PlayerTally tally) {
        PlayerStatsSummary summary = new PlayerStatsSummary();
        summary.setTeamId(teamId);
        summary.setPlayerId(playerId);
        summary.setSeason(season);
        summary.setAppearances(tally.appearances());
        summary.setGoals(tally.goals());
        summary.setAssists(tally.assists());
        summary.setOwnGoals(tally.ownGoals());
        summary.setInternalAppearances(tally.internalAppearances());
        summary.setInternalWins(tally.internalWins());
        return summary;
    }
}
