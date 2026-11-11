package com.yunhefc.attendance.controller;

import com.yunhefc.attendance.application.AttendanceApplicationService;
import com.yunhefc.attendance.dto.AttendanceDtos.AttendanceResponse;
import com.yunhefc.attendance.dto.AttendanceDtos.MyAttendanceResponse;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/matches/{matchId}")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceApplicationService attendanceApplicationService;

    @PostMapping("/check-in")
    @PreAuthorize("hasAuthority('attendance:checkin:own')")
    @Idempotent("ATT-01")
    public ApiResponse<AttendanceResponse> checkIn(@PathVariable Long matchId) {
        return ApiResponse.ok(
                new AttendanceResponse(attendanceApplicationService.checkIn(matchId)));
    }

    @GetMapping("/attendance/me")
    public ApiResponse<MyAttendanceResponse> myAttendance(@PathVariable Long matchId) {
        return ApiResponse.ok(
                new MyAttendanceResponse(attendanceApplicationService.myAttendance(matchId)));
    }
}
