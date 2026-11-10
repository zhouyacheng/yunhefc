package com.yunhefc.match.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class MatchDtos {

    private MatchDtos() {
    }

    public record MatchAdminQuery(
            Integer status,
            Integer type,
            String season,
            String scope,
            String sort,
            long page,
            long pageSize) {
    }

    public record MatchPublicQuery(
            Integer type,
            String venue,
            Integer status,
            String season,
            String month,
            String scope,
            String sort,
            long page,
            long pageSize) {
    }

    public record MatchCreateRequest(
            @NotBlank @Size(max = 128) String title,
            @NotNull @Min(0) @Max(3) Integer type,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @NotBlank @Size(max = 128) String venue,
            @NotBlank @Size(max = 64) String homeTeamName,
            @NotBlank @Size(max = 64) String awayTeamName,
            @Size(max = 16) String format,
            @Size(max = 64) String competition,
            String season,
            @Min(0) @Max(65535) Integer round,
            @Min(0) @Max(1) Integer ourSide,
            @Min(1) @Max(65535) Integer capacity,
            @Min(0) @Max(65535) Integer guestCapacity,
            @Min(0) @Max(65535) Integer waitlistCapacity,
            Boolean includeGuests,
            @Min(0) @Max(255) Integer scoreHome,
            @Min(0) @Max(255) Integer scoreAway) {
    }

    public record MatchAdminView(
            Long id,
            String title,
            Integer type,
            String typeLabel,
            String format,
            Instant startAt,
            Instant endAt,
            Instant signupDeadline,
            Instant cancelLock,
            String venue,
            String competition,
            String season,
            Integer round,
            String homeTeamName,
            String awayTeamName,
            Integer ourSide,
            String ourSideLabel,
            Integer status,
            String statusLabel,
            boolean manualStatus,
            Integer capacity,
            Integer guestCapacity,
            Integer waitlistCapacity,
            boolean includeGuests,
            Integer scoreHome,
            Integer scoreAway,
            Integer seasonMatchNo,
            Integer totalMatchNo,
            Integer version,
            Instant statusUpdatedAt,
            Long statusUpdatedBy,
            Long createdBy,
            Instant createdAt,
            Instant updatedAt) {
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

    public record MatchDetail(
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
            int waitlistCount,
            String format,
            String competition,
            Instant signupDeadline,
            Instant cancelLock,
            Integer capacity,
            Integer guestCapacity,
            Integer waitlistCapacity,
            Integer version,
            DerivedScore derivedScore,
            RegistrationView myRegistration) {
    }

    public record DerivedScore(Integer scoreHome, Integer scoreAway) {
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
}
