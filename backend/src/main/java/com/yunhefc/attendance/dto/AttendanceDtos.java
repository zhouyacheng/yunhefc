package com.yunhefc.attendance.dto;

import java.time.Instant;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    public record AttendanceView(
            Long id,
            Long playerId,
            Integer status,
            String statusLabel,
            Integer source,
            String sourceLabel,
            Instant recordedAt) {
    }

    public record AttendanceResponse(AttendanceView attendance) {
    }

    public record MyAttendanceResponse(AttendanceView attendance) {
    }
}
