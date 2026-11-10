package com.yunhefc.match.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.match.application.MatchAdminApplicationService;
import com.yunhefc.match.dto.MatchDtos.MatchAdminQuery;
import com.yunhefc.match.dto.MatchDtos.MatchAdminView;
import com.yunhefc.match.dto.MatchDtos.MatchCancelRequest;
import com.yunhefc.match.dto.MatchDtos.MatchCreateRequest;
import com.yunhefc.match.dto.MatchDtos.MatchScheduleRequest;
import com.yunhefc.match.dto.MatchDtos.MatchStatusRequest;
import com.yunhefc.match.dto.MatchDtos.MatchUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/matches")
@RequiredArgsConstructor
public class MatchAdminController {

    private final MatchAdminApplicationService matchAdminService;

    @PostMapping
    @PreAuthorize("hasAuthority('match:create:any')")
    @Idempotent("MAT-04")
    public ApiResponse<MatchAdminView> create(@Valid @RequestBody MatchCreateRequest request) {
        return ApiResponse.ok(matchAdminService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('match:view:any')")
    public ApiResponse<PageResponse<MatchAdminView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(matchAdminService.list(
                new MatchAdminQuery(status, type, season, scope, sort, page, pageSize)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('match:view:any')")
    public ApiResponse<MatchAdminView> detail(@PathVariable Long id) {
        return ApiResponse.ok(matchAdminService.detail(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('match:edit:any')")
    public ApiResponse<MatchAdminView> update(@PathVariable Long id,
                                              @Valid @RequestBody MatchUpdateRequest request) {
        return ApiResponse.ok(matchAdminService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('match:status:any')")
    @Idempotent("MAT-06")
    public ApiResponse<MatchAdminView> changeStatus(@PathVariable Long id,
                                                    @Valid @RequestBody MatchStatusRequest request) {
        return ApiResponse.ok(matchAdminService.changeStatus(id, request));
    }

    @PostMapping("/{id}/postpone")
    @PreAuthorize("hasAuthority('match:edit:any')")
    @Idempotent("MAT-07")
    public ApiResponse<MatchAdminView> postpone(@PathVariable Long id,
                                                @Valid @RequestBody MatchScheduleRequest request) {
        return ApiResponse.ok(matchAdminService.postpone(id, request));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('match:edit:any')")
    @Idempotent("MAT-11")
    public ApiResponse<MatchAdminView> reschedule(@PathVariable Long id,
                                                  @Valid @RequestBody MatchScheduleRequest request) {
        return ApiResponse.ok(matchAdminService.reschedule(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('match:status:any')")
    @Idempotent("MAT-08")
    public ApiResponse<MatchAdminView> cancel(@PathVariable Long id,
                                              @Valid @RequestBody MatchCancelRequest request) {
        return ApiResponse.ok(matchAdminService.cancel(id, request));
    }
}
