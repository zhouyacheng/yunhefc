package com.yunhefc.stats.application;

import com.yunhefc.contracts.attendance.AttendanceReadPort;
import com.yunhefc.contracts.event.EventStatsReadPort;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.stats.domain.PlayerStatsSummary;
import com.yunhefc.stats.domain.PlayerTally;
import com.yunhefc.stats.domain.StatsAggregator;
import com.yunhefc.stats.infrastructure.StatsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本赛季全量重算：按球员覆盖写汇总表并刷新 Redis 缓存；计算完成前不触碰旧结果。
 */
@Service
@RequiredArgsConstructor
public class StatsRollupApplicationService {

    private final MatchReadPort matchReadPort;
    private final AttendanceReadPort attendanceReadPort;
    private final EventStatsReadPort eventStatsReadPort;
    private final PlayerReadPort playerReadPort;
    private final TeamReadPort teamReadPort;
    private final StatsSummaryReader summaryReader;
    private final StatsSummaryWriter summaryWriter;
    private final StatsCache statsCache;
    private final StatsQueryService statsQueryService;
    private final Clock clock;

    public RollupResult rollUpCurrentSeason() {
        Long teamId = teamReadPort.currentTeamId();
        String season = statsQueryService.currentSeason();
        List<MatchSnapshot> matches = matchReadPort.findSeasonMatches(teamId, season);
        List<Long> matchIds = matches.stream().map(MatchSnapshot::id).toList();

        Map<Long, PlayerTally> tallies = StatsAggregator.tally(matches,
                attendanceReadPort.findByMatchIds(matchIds),
                eventStatsReadPort.findByMatchIds(matchIds));

        // 已迁移/已删除球员仍保留历史行；只覆盖本队现存球员，且把已归零的旧行写回 0。
        Set<Long> candidates = new HashSet<>(tallies.keySet());
        candidates.addAll(summaryReader.playerIds(teamId, season));
        Set<Long> teamPlayerIds = playerReadPort.findByIds(teamId, candidates).stream()
                .map(PlayerSnapshot::id)
                .collect(Collectors.toSet());

        Instant now = Instant.now(clock);
        List<PlayerStatsSummary> summaries = teamPlayerIds.stream()
                .sorted()
                .map(playerId -> summary(teamId, season, playerId,
                        tallies.getOrDefault(playerId, PlayerTally.ZERO)))
                .toList();
        summaryWriter.replace(summaries, now);

        // 缓存只在汇总表事务提交后刷新，避免回滚后缓存领先于数据库。
        List<PlayerStatsRow> rows = summaries.stream()
                .map(summary -> PlayerStatsRow.of(summary, now))
                .toList();
        statsCache.write(teamId, season, rows);
        return new RollupResult(teamId, season, matches.size(), rows.size());
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
        return summary;
    }
}
