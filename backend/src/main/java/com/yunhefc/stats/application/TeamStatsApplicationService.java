package com.yunhefc.stats.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.stats.dto.TeamStatsDtos.PlayerStatsView;
import com.yunhefc.stats.dto.TeamStatsDtos.TeamStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 球队页统计：汇总表只含已产出数据的球员，缺失行按 0 补齐。
 */
@Service
@RequiredArgsConstructor
public class TeamStatsApplicationService {

    private static final Pattern SEASON_PATTERN = Pattern.compile("\\d{4}");

    private final TeamReadPort teamReadPort;
    private final StatsQueryService statsQueryService;

    @Transactional(readOnly = true)
    public TeamStatsView teamStats(String requestedSeason) {
        String season = resolveSeason(requestedSeason);
        Long teamId = teamReadPort.currentTeamId();
        List<PlayerStatsView> items = statsQueryService.rosterStats(teamId, season).stream()
                .map(roster -> roster.stats())
                .map(summary -> new PlayerStatsView(
                        summary.playerId(),
                        summary.season(),
                        summary.appearances(),
                        summary.goals(),
                        summary.assists(),
                        summary.updatedAt()))
                .toList();
        return new TeamStatsView(season, items);
    }

    private String resolveSeason(String requestedSeason) {
        if (requestedSeason == null || requestedSeason.isBlank()) {
            return statsQueryService.currentSeason();
        }
        String season = requestedSeason.trim();
        if (!SEASON_PATTERN.matcher(season).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "season 必须为四位年份");
        }
        return season;
    }
}
