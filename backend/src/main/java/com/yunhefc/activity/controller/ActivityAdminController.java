package com.yunhefc.activity.controller;

import com.yunhefc.activity.application.ActivityAdminApplicationService;
import com.yunhefc.activity.dto.ActivityDtos.ActivityAdmin;
import com.yunhefc.activity.dto.ActivityDtos.ActivityAdminQuery;
import com.yunhefc.activity.dto.ActivityDtos.ActivityCreateRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupRemoveResult;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupView;
import com.yunhefc.activity.dto.ActivityDtos.ActivityStatusRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivityUpdateRequest;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/activities")
@RequiredArgsConstructor
public class ActivityAdminController {

    private final ActivityAdminApplicationService activityAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('activity:manage:any')")
    public ApiResponse<PageResponse<ActivityAdmin>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(activityAdminService.list(
                new ActivityAdminQuery(type, status, keyword, page, pageSize)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('activity:manage:any')")
    public ApiResponse<ActivityAdmin> detail(@PathVariable Long id) {
        return ApiResponse.ok(activityAdminService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('activity:manage:any')")
    @Idempotent("ACT-08")
    public ApiResponse<ActivityAdmin> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ApiResponse.ok(activityAdminService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('activity:manage:any')")
    @Idempotent("ACT-09")
    public ApiResponse<ActivityAdmin> update(@PathVariable Long id,
                                             @Valid @RequestBody ActivityUpdateRequest request) {
        return ApiResponse.ok(activityAdminService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('activity:manage:any')")
    @Idempotent("ACT-10")
    public ApiResponse<ActivityAdmin> changeStatus(@PathVariable Long id,
                                                   @Valid @RequestBody ActivityStatusRequest request) {
        return ApiResponse.ok(activityAdminService.changeStatus(id, request));
    }

    @GetMapping("/{id}/signups")
    @PreAuthorize("hasAuthority('activity:manage:any')")
    public ApiResponse<PageResponse<ActivitySignupView>> roster(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(activityAdminService.roster(id, page, pageSize));
    }

    @DeleteMapping("/{id}/signups/{signupId}")
    @PreAuthorize("hasAuthority('activity:manage:any')")
    @Idempotent("ACT-12")
    public ApiResponse<ActivitySignupRemoveResult> removeSignup(@PathVariable Long id,
                                                                @PathVariable Long signupId) {
        return ApiResponse.ok(activityAdminService.removeSignup(id, signupId));
    }
}
