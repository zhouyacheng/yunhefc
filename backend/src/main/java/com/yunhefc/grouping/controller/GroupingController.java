package com.yunhefc.grouping.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.grouping.application.GroupingApplicationService;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/matches/{matchId}/grouping")
@RequiredArgsConstructor
public class GroupingController {

    private final GroupingApplicationService groupingApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('grouping:view:any')")
    public ApiResponse<GroupingView> view(@PathVariable Long matchId) {
        return ApiResponse.ok(groupingApplicationService.viewForPlayer(matchId));
    }
}
