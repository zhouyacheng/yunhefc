package com.yunhefc.query.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.query.application.RegistrationRosterApplicationService;
import com.yunhefc.query.dto.RegistrationDtos.RegistrationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegistrationRosterController {

    private final RegistrationRosterApplicationService rosterService;

    @GetMapping("/api/v1/c/matches/{matchId}/registrations")
    @PreAuthorize("hasAuthority('attendance:view:any')")
    public ApiResponse<PageResponse<RegistrationRow>> listForPlayer(
            @PathVariable Long matchId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(rosterService.list(matchId, type, status, page, pageSize));
    }

    @GetMapping("/api/v1/b/matches/{matchId}/registrations")
    @PreAuthorize("hasAuthority('attendance:view:any')")
    public ApiResponse<PageResponse<RegistrationRow>> listForAdmin(
            @PathVariable Long matchId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(rosterService.list(matchId, type, status, page, pageSize));
    }
}
