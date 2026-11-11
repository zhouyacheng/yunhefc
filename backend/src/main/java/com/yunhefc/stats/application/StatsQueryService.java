package com.yunhefc.stats.application;

import com.yunhefc.contracts.player.PlayerPublicSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.stats.infrastructure.StatsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final StatsSummaryReader summaryReader;
    private final PlayerReadPort playerReadPort;
    private final StatsCache statsCache;
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
    public List<RosterStats> rosterStats(Long teamId, String season) {
        Map<Long, PlayerStatsRow> summaries = seasonStats(teamId, season);
        return playerReadPort.findPublicRoster(teamId).stream()
                .sorted(Comparator.comparing(PlayerPublicSnapshot::id))
                .map(player -> new RosterStats(player, summaries.getOrDefault(player.id(),
                        new PlayerStatsRow(player.id(), season, 0, 0, 0, null))))
                .toList();
    }

    /**
     * 赛季汇总：优先读缓存，未命中回源汇总表并回填缓存。
     */
    public Map<Long, PlayerStatsRow> seasonStats(Long teamId, String season) {
        return statsCache.read(teamId, season)
                .map(StatsQueryService::indexed)
                .orElseGet(() -> loadAndCache(teamId, season));
    }

    private Map<Long, PlayerStatsRow> loadAndCache(Long teamId, String season) {
        List<PlayerStatsRow> rows = summaryReader.load(teamId, season);
        statsCache.write(teamId, season, rows);
        return indexed(rows);
    }

    private static Map<Long, PlayerStatsRow> indexed(List<PlayerStatsRow> rows) {
        Map<Long, PlayerStatsRow> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put(row.playerId(), row));
        return indexed;
    }
}
