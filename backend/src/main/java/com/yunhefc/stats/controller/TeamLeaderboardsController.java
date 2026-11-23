package com.yunhefc.stats.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.stats.application.TeamLeaderboardApplicationService;
import com.yunhefc.stats.dto.LeaderboardDtos.LeaderboardView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/team/leaderboards")
@RequiredArgsConstructor
public class TeamLeaderboardsController {

    private final TeamLeaderboardApplicationService leaderboardService;

    @GetMapping
    public ApiResponse<LeaderboardView> leaderboards(
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String scope) {
        return ApiResponse.ok(leaderboardService.leaderboards(season, scope));
    }
}
