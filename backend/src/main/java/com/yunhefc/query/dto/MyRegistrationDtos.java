package com.yunhefc.query.dto;

import com.yunhefc.query.dto.RegistrationDtos.RegistrationRow;

import java.time.Instant;

public final class MyRegistrationDtos {

    private MyRegistrationDtos() {
    }

    public record MyRegistration(RegistrationRow registration, MatchSummary match) {
    }

    public record MatchSummary(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            Instant startAt,
            Instant endAt,
            String venue,
            Integer round,
            String season,
            String homeTeamName,
            String awayTeamName,
            Integer ourSide,
            String ourSideLabel,
            Integer scoreHome,
            Integer scoreAway,
            String resultText,
            Integer status,
            String statusLabel,
            Integer totalMatchNo,
            Integer seasonMatchNo,
            int confirmedCount,
            int guestCount,
            int waitlistCount) {
    }

    /**
     * REG-04 本人报名状态；未报名时 registration 为 null，M05 落地前 attendance 恒为 null。
     */
    public record MyMatchRegistrationView(RegistrationView registration, AttendanceView attendance) {
    }

    public record RegistrationView(
            Long id,
            Long playerId,
            Integer type,
            String typeLabel,
            Integer status,
            String statusLabel,
            Instant registeredAt,
            Instant cancelledAt,
            boolean active) {
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
}
