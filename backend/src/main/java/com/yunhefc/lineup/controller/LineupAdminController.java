package com.yunhefc.lineup.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.lineup.application.LineupApplicationService;
import com.yunhefc.lineup.dto.LineupDtos.LineupAdminView;
import com.yunhefc.lineup.dto.LineupDtos.LineupSaveRequest;
import com.yunhefc.lineup.dto.LineupDtos.LineupSaveResponse;
import com.yunhefc.lineup.dto.LineupDtos.LineupTemplateRequest;
import com.yunhefc.lineup.dto.LineupDtos.LineupTemplateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/matches/{matchId}/lineups")
@RequiredArgsConstructor
public class LineupAdminController {

    private final LineupApplicationService lineupApplicationService;

    @GetMapping("/{side}")
    @PreAuthorize("hasAuthority('lineup:view:any')")
    public ApiResponse<LineupAdminView> view(@PathVariable Long matchId,
                                             @PathVariable Integer side) {
        return ApiResponse.ok(lineupApplicationService.adminView(matchId, side));
    }

    @PutMapping("/{side}")
    @PreAuthorize("hasAuthority('lineup:edit:any')")
    @Idempotent("LIN-03")
    public ApiResponse<LineupSaveResponse> save(@PathVariable Long matchId,
                                                @PathVariable Integer side,
                                                @Valid @RequestBody LineupSaveRequest request) {
        return ApiResponse.ok(lineupApplicationService.save(matchId, side, request));
    }

    @PostMapping("/{side}/template")
    @PreAuthorize("hasAuthority('lineup:edit:any')")
    public ApiResponse<LineupTemplateResponse> saveAsTemplate(
            @PathVariable Long matchId,
            @PathVariable Integer side,
            @Valid @RequestBody LineupTemplateRequest request) {
        return ApiResponse.ok(new LineupTemplateResponse(
                lineupApplicationService.saveAsTemplate(matchId, side, request.name())));
    }
}
