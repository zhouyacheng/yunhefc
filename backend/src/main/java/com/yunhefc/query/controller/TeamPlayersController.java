package com.yunhefc.query.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.query.application.TeamPlayersApplicationService;
import com.yunhefc.query.dto.TeamPlayerDtos.TeamPlayersView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/team/players")
@RequiredArgsConstructor
public class TeamPlayersController {

    private final TeamPlayersApplicationService teamPlayersService;

    @GetMapping
    public ApiResponse<TeamPlayersView> list(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "false") boolean all) {
        return ApiResponse.ok(teamPlayersService.list(limit, all));
    }
}
