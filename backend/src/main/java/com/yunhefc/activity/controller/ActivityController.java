package com.yunhefc.activity.controller;

import com.yunhefc.activity.application.ActivityAdminApplicationService;
import com.yunhefc.activity.application.ActivityApplicationService;
import com.yunhefc.activity.dto.ActivityDtos.ActivityAdmin;
import com.yunhefc.activity.dto.ActivityDtos.ActivityCancelResult;
import com.yunhefc.activity.dto.ActivityDtos.ActivityQuery;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupResponse;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupView;
import com.yunhefc.activity.dto.ActivityDtos.ActivityStatusRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySummary;
import com.yunhefc.activity.dto.ActivityDtos.ActivityTimeQuery;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityApplicationService activityService;
    private final ActivityAdminApplicationService activityAdminService;

    @GetMapping
    public ApiResponse<PageResponse<ActivitySummary>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(activityService.list(
                new ActivityQuery(type, status, scope, sort,
                        new ActivityTimeQuery(month, startDate, endDate), page, pageSize)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivitySummary> detail(@PathVariable Long id) {
        return ApiResponse.ok(activityService.detail(id));
    }

    @PostMapping("/{id}/signups")
    @PreAuthorize("hasAuthority('activity:signup:own')")
    @Idempotent("ACT-03")
    public ApiResponse<ActivitySignupResponse> signup(@PathVariable Long id,
                                                      @Valid @RequestBody ActivitySignupRequest request) {
        return ApiResponse.ok(activityService.signup(id, request));
    }

    @DeleteMapping("/{id}/signups/me")
    @PreAuthorize("hasAuthority('activity:signup:own')")
    @Idempotent("ACT-04")
    public ApiResponse<ActivityCancelResult> cancel(@PathVariable Long id) {
        return ApiResponse.ok(activityService.cancel(id));
    }

    @GetMapping("/{id}/signups")
    @PreAuthorize("hasAuthority('activity:view:any')")
    public ApiResponse<PageResponse<ActivitySignupView>> roster(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(activityService.roster(id, page, pageSize));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('activity:manage:any')")
    @Idempotent("ACT-13")
    public ApiResponse<ActivityAdmin> changeStatus(@PathVariable Long id,
                                                   @Valid @RequestBody ActivityStatusRequest request) {
        return ApiResponse.ok(activityAdminService.changeStatus(id, request));
    }
}
