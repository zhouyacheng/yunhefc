package com.yunhefc.grouping.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.grouping.application.GroupingApplicationService;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingAdminView;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingGenerateRequest;
import com.yunhefc.grouping.dto.GroupingDtos.GroupingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/matches/{matchId}/grouping")
@RequiredArgsConstructor
public class GroupingAdminController {

    private final GroupingApplicationService groupingApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('grouping:view:any')")
    public ApiResponse<GroupingAdminView> view(@PathVariable Long matchId) {
        return ApiResponse.ok(groupingApplicationService.adminView(matchId));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('grouping:generate:any')")
    @Idempotent("GRP-03")
    public ApiResponse<GroupingResponse> generate(
            @PathVariable Long matchId,
            @RequestBody(required = false) GroupingGenerateRequest request) {
        Boolean confirm = request == null ? null : request.confirm();
        return ApiResponse.ok(new GroupingResponse(
                groupingApplicationService.generate(matchId, confirm)));
    }
}
