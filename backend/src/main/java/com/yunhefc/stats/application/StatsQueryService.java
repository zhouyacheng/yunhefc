package com.yunhefc.stats.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.player.PlayerPublicSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.stats.domain.PlayerStatsSummary;
import com.yunhefc.stats.infrastructure.PlayerStatsSummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计只读接口：球队页与分组读取汇总结果，允许最多 10 分钟延迟。
 */
@Service
@RequiredArgsConstructor
public class StatsQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PlayerStatsSummaryMapper statsMapper;
    private final PlayerReadPort playerReadPort;
    private final Clock clock;

    /**
     * 本赛季：按 Asia/Shanghai 的当前年份。
     */
    public String currentSeason() {
        return String.valueOf(LocalDate.now(clock.withZone(BUSINESS_ZONE)).getYear());
    }

    /**
     * 球队页公开名单的赛季汇总；无汇总行的球员按 0 补齐。
     */
    @Transactional(readOnly = true)
    public List<RosterStats> rosterStats(Long teamId, String season) {
        Map<Long, PlayerStatsRow> summaries = seasonStats(teamId, season);
        return playerReadPort.findPublicRoster(teamId).stream()
                .sorted(Comparator.comparing(PlayerPublicSnapshot::id))
                .map(player -> new RosterStats(player, summaries.getOrDefault(player.id(),
                        new PlayerStatsRow(player.id(), season, 0, 0, 0, null))))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, PlayerStatsRow> seasonStats(Long teamId, String season) {
        Map<Long, PlayerStatsRow> rows = new LinkedHashMap<>();
        statsMapper.selectList(new LambdaQueryWrapper<PlayerStatsSummary>()
                        .eq(PlayerStatsSummary::getTeamId, teamId)
                        .eq(PlayerStatsSummary::getSeason, season))
                .forEach(stats -> rows.put(stats.getPlayerId(), new PlayerStatsRow(
                        stats.getPlayerId(),
                        stats.getSeason(),
                        stats.getAppearances(),
                        stats.getGoals(),
                        stats.getAssists(),
                        stats.getUpdatedAt())));
        return rows;
    }
}
