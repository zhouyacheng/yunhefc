package com.yunhefc.stats.application;

import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.stats.dto.LeaderboardDtos.LeaderboardView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 排行榜公开读接口：确定当前球队后由查询服务组装全员指标。
 */
@Service
@RequiredArgsConstructor
public class TeamLeaderboardApplicationService {

    private final TeamReadPort teamReadPort;
    private final StatsQueryService statsQueryService;

    @Transactional(readOnly = true)
    public LeaderboardView leaderboards(String requestedSeason, String requestedScope) {
        return statsQueryService.leaderboard(
                teamReadPort.currentTeamId(), requestedSeason, requestedScope);
    }
}
