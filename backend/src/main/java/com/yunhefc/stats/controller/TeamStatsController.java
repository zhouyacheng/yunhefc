package com.yunhefc.stats.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.stats.application.TeamStatsApplicationService;
import com.yunhefc.stats.dto.TeamStatsDtos.TeamStatsView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/team/stats")
@RequiredArgsConstructor
public class TeamStatsController {

    private final TeamStatsApplicationService teamStatsService;

    @GetMapping
    public ApiResponse<TeamStatsView> teamStats(@RequestParam(required = false) String season) {
        return ApiResponse.ok(teamStatsService.teamStats(season));
    }
}
