package com.yunhefc.stats.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.contracts.player.PlayerPublicSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.stats.dto.LeaderboardDtos.LeaderboardEntry;
import com.yunhefc.stats.dto.LeaderboardDtos.LeaderboardTotals;
import com.yunhefc.stats.dto.LeaderboardDtos.LeaderboardView;
import com.yunhefc.stats.infrastructure.StatsCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 统计只读接口：球队页与分组读取汇总结果，允许最多 1 分钟延迟。
 */
@Service
@RequiredArgsConstructor
public class StatsQueryService {

    private static final Pattern SEASON_PATTERN = Pattern.compile("\\d{4}");
    private static final Predicate<String> IS_SEASON = SEASON_PATTERN.asMatchPredicate();
    private static final String SCOPE_SEASON = "season";
    private static final String SCOPE_ALL = "all";

    private final StatsSummaryReader summaryReader;
    private final PlayerReadPort playerReadPort;
    private final StatsCache statsCache;

    /**
     * 本赛季：按 Asia/Shanghai 的当前年份。
     */
    public String currentSeason() {
        return String.valueOf(LocalDate.now(ShanghaiTime.ZONE).getYear());
    }

    /**
     * 请求赛季：缺省取当前赛季，显式值必须为四位年份。
     */
    public String resolveSeason(String requestedSeason) {
        if (requestedSeason == null || requestedSeason.isBlank()) {
            return currentSeason();
        }
        String season = requestedSeason.trim();
        if (!IS_SEASON.test(season)) {
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
                        new PlayerStatsRow(player.id(), season, 0, 0, 0, 0, 0, 0, null))))
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

    /**
     * 排行榜：一次返回全员指标，排序与名次由前端计算；scope=all 忽略 season。
     */
    public LeaderboardView leaderboard(Long teamId, String requestedSeason, String requestedScope) {
        String scope = requestedScope == null || requestedScope.isBlank()
                ? SCOPE_SEASON : requestedScope.trim();
        if (!SCOPE_SEASON.equals(scope) && !SCOPE_ALL.equals(scope)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "scope 必须为 season 或 all");
        }
        String season = SCOPE_ALL.equals(scope) ? null : resolveSeason(requestedSeason);
        boolean includeTotals = !SCOPE_ALL.equals(scope);
        int currentYear = Integer.parseInt(currentSeason());
        Map<Long, Metrics> careerTotals = metricsUpTo(teamId, currentYear, true);
        Map<Long, Metrics> metrics = SCOPE_ALL.equals(scope)
                ? careerTotals
                : seasonMetrics(teamId, season);
        Map<Long, Metrics> historical = includeTotals
                ? metricsUpTo(teamId, currentYear, false)
                : Map.of();
        List<LeaderboardEntry> items = playerReadPort.findPublicRoster(teamId).stream()
                .sorted(Comparator.comparing(PlayerPublicSnapshot::id))
                .map(player -> entry(player,
                        metrics.getOrDefault(player.id(), Metrics.ZERO),
                        includeTotals
                                ? new Cumulative(
                                        historical.getOrDefault(player.id(), Metrics.ZERO),
                                        careerTotals.getOrDefault(player.id(), Metrics.ZERO))
                                : null))
                .toList();
        return new LeaderboardView(season, scope, items);
    }

    private Map<Long, Metrics> seasonMetrics(Long teamId, String season) {
        Map<Long, Metrics> metrics = new HashMap<>();
        seasonStats(teamId, season)
                .forEach((playerId, row) -> metrics.put(playerId, Metrics.of(row)));
        return metrics;
    }

    /**
     * 汇总到当前自然年：inclusive 时含当前赛季（生涯累计），否则只含更早赛季（历史累计）。
     */
    private Map<Long, Metrics> metricsUpTo(Long teamId, int currentYear, boolean inclusive) {
        return aggregateSeasons(teamId, season ->
                IS_SEASON.test(season) && (Integer.parseInt(season) < currentYear
                        || (inclusive && Integer.parseInt(season) == currentYear)));
    }

    private Map<Long, Metrics> aggregateSeasons(Long teamId, Predicate<String> include) {
        Map<Long, Metrics> totals = new HashMap<>();
        for (String season : summaryReader.seasons(teamId)) {
            if (!include.test(season)) {
                continue;
            }
            seasonStats(teamId, season).forEach((playerId, row) ->
                    totals.merge(playerId, Metrics.of(row), Metrics::plus));
        }
        return totals;
    }

    private static LeaderboardEntry entry(PlayerPublicSnapshot player, Metrics metrics,
                                          Cumulative cumulative) {
        LeaderboardTotals current = totalsOf(metrics);
        return new LeaderboardEntry(
                player.id(),
                player.name(),
                player.jersey(),
                player.position(),
                player.avatarUrl(),
                current.appearances(),
                current.goals(),
                current.assists(),
                current.ownGoals(),
                current.internalAppearances(),
                current.internalWins(),
                current.winRate(),
                current.goalsPerMatch(),
                current.assistsPerMatch(),
                cumulative == null ? null : totalsOf(cumulative.history()),
                cumulative == null ? null : totalsOf(cumulative.career()));
    }

    private static LeaderboardTotals totalsOf(Metrics metrics) {
        return new LeaderboardTotals(
                metrics.appearances(),
                metrics.goals(),
                metrics.assists(),
                metrics.ownGoals(),
                metrics.internalAppearances(),
                metrics.internalWins(),
                winRate(metrics),
                perMatch(metrics.goals(), metrics.appearances()),
                perMatch(metrics.assists(), metrics.appearances()));
    }

    /**
     * 胜率 0–100 保留 1 位小数；分母为 0 时返回 null。
     */
    private static BigDecimal winRate(Metrics metrics) {
        if (metrics.internalAppearances() == 0) {
            return null;
        }
        return BigDecimal.valueOf(metrics.internalWins())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(metrics.internalAppearances()),
                        1, RoundingMode.HALF_UP);
    }

    /**
     * 场均保留 2 位小数；无出场返回 0.00（前端只展示出场 > 0 的球员）。
     */
    private static BigDecimal perMatch(int value, int appearances) {
        if (appearances == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(appearances), 2, RoundingMode.HALF_UP);
    }

    private record Cumulative(Metrics history, Metrics career) {
    }

    private record Metrics(int appearances, int goals, int assists,
                           int ownGoals, int internalAppearances, int internalWins) {

        private static final Metrics ZERO = new Metrics(0, 0, 0, 0, 0, 0);

        private static Metrics of(PlayerStatsRow row) {
            return new Metrics(row.appearances(), row.goals(), row.assists(),
                    row.ownGoals(), row.internalAppearances(), row.internalWins());
        }

        private Metrics plus(Metrics other) {
            return new Metrics(appearances + other.appearances, goals + other.goals,
                    assists + other.assists, ownGoals + other.ownGoals,
                    internalAppearances + other.internalAppearances,
                    internalWins + other.internalWins);
        }
    }
}
