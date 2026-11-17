package com.yunhefc.stats.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.player.PlayerPublicSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.stats.infrastructure.StatsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 统计只读接口：球队页与分组读取汇总结果，允许最多 1 分钟延迟。
 */
@Service
@RequiredArgsConstructor
public class StatsQueryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern SEASON_PATTERN = Pattern.compile("\\d{4}");

    private final StatsSummaryReader summaryReader;
    private final PlayerReadPort playerReadPort;
    private final StatsCache statsCache;

    /**
     * 本赛季：按 Asia/Shanghai 的当前年份。
     */
    public String currentSeason() {
        return String.valueOf(LocalDate.now(BUSINESS_ZONE).getYear());
    }

    /**
     * 请求赛季：缺省取当前赛季，显式值必须为四位年份。
     */
    public String resolveSeason(String requestedSeason) {
        if (requestedSeason == null || requestedSeason.isBlank()) {
            return currentSeason();
        }
        String season = requestedSeason.trim();
        if (!SEASON_PATTERN.matcher(season).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "season 必须为四位年份");
        }
        return season;
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
