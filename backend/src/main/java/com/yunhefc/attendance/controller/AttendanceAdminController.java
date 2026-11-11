package com.yunhefc.attendance.controller;

import com.yunhefc.attendance.application.AttendanceApplicationService;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceMarkRequest;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceResponse;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceRow;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceTempRequest;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.idempotency.Idempotent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/matches/{matchId}/attendance")
@RequiredArgsConstructor
public class AttendanceAdminController {

    private final AttendanceApplicationService attendanceApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('attendance:view:any')")
    public ApiResponse<PageResponse<AttendanceRow>> list(
            @PathVariable Long matchId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(attendanceApplicationService.list(matchId, status, page, pageSize));
    }

    @PutMapping("/{playerId}")
    @PreAuthorize("hasAuthority('attendance:mark:any')")
    @Idempotent("ATT-05")
    public ApiResponse<AttendanceResponse> mark(
            @PathVariable Long matchId,
            @PathVariable Long playerId,
            @Valid @RequestBody AttendanceMarkRequest request) {
        return ApiResponse.ok(new AttendanceResponse(
                attendanceApplicationService.mark(matchId, playerId, request.status())));
    }

    @PostMapping("/temp")
    @PreAuthorize("hasAuthority('attendance:mark:any')")
    @Idempotent("ATT-06")
    public ApiResponse<AttendanceResponse> tempPresent(
            @PathVariable Long matchId,
            @Valid @RequestBody AttendanceTempRequest request) {
        return ApiResponse.ok(new AttendanceResponse(
                attendanceApplicationService.tempPresent(matchId, request.playerId())));
    }
}
