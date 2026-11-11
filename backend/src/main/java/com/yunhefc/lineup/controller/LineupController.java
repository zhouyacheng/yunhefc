package com.yunhefc.lineup.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.lineup.application.LineupApplicationService;
import com.yunhefc.lineup.dto.LineupDtos.LineupView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/matches/{matchId}/lineups")
@RequiredArgsConstructor
public class LineupController {

    private final LineupApplicationService lineupApplicationService;

    /**
     * LIN-01：正式球员只读；外挂无 lineup:view:any 权限。对手方仅阵型与空槽位。
     */
    @GetMapping("/{side}")
    @PreAuthorize("hasAuthority('lineup:view:any')")
    public ApiResponse<LineupView> view(@PathVariable Long matchId,
                                        @PathVariable Integer side) {
        return ApiResponse.ok(lineupApplicationService.viewForPlayer(matchId, side));
    }
}
