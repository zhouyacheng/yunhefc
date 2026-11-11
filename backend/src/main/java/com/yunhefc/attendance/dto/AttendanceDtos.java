package com.yunhefc.attendance.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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

    /**
     * ATT-03/04 考勤行：考勤记录附带球员展示字段。
     */
    public record AttendanceRow(
            Long id,
            PlayerRef player,
            Integer status,
            String statusLabel,
            Integer source,
            String sourceLabel,
            Instant recordedAt) {
    }

    public record PlayerRef(
            Long id,
            String name,
            Integer jersey,
            Integer position,
            String positionLabel) {
    }

    public record AttendanceResponse(AttendanceView attendance) {
    }

    public record MyAttendanceResponse(AttendanceView attendance) {
    }

    /**
     * ATT-05 只接受正常/缺勤；临时到场走 ATT-06。
     */
    public record AttendanceMarkRequest(@NotNull Integer status) {
    }

    public record AttendanceTempRequest(@NotNull @Min(1) Long playerId) {
    }
}
