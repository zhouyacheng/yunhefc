package com.yunhefc.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SmsCodeRequest(
            @NotBlank String phone,
            @NotBlank String purpose) {
    }

    public record RegisterRequest(
            @NotBlank String phone,
            @NotBlank String code,
            @NotBlank String password,
            @NotBlank String name,
            @NotNull Integer position,
            String inviteToken) {
    }

    public record LoginRequest(
            @NotBlank String phone,
            @NotBlank String password) {
    }

    public record ResetPasswordRequest(
            @NotBlank String phone,
            @NotBlank String code,
            @NotBlank String newPassword) {
    }

    public record UserView(
            Long id,
            String name,
            String phoneMasked,
            Integer status,
            String statusLabel,
            Set<String> roles,
            LocalDateTime createdAt) {
    }

    public record PlayerView(
            Long id,
            String name,
            Integer jersey,
            Integer type,
            String typeLabel,
            Integer position,
            String positionLabel,
            Integer skill) {
    }

    public record AuthResult(UserView user, PlayerView player, Set<String> permissions) {
    }

    public record AuthResponse(UserView user, PlayerView player, Set<String> permissions, String csrfToken) {
    }

    public record MeView(
            UserView user,
            PlayerView player,
            Set<String> roles,
            Set<String> permissions,
            int unreadCount) {
    }

    public record BMeView(
            UserView user,
            Set<String> permissions,
            List<String> sections) {
    }

    public record UserSummary(
            Long id,
            String name,
            String phoneMasked,
            Integer status,
            String statusLabel,
            Set<String> roles,
            Integer type,
            String typeLabel) {
    }

    public record PhoneUpdateRequest(@NotBlank String phone) {
    }

    public record InviteCreateRequest(Integer type, Long matchId, Integer expiresInDays, Integer maxUses) {
    }

    public record MatchInviteCreateRequest(Integer expiresInDays, Integer maxUses) {
    }

    public record TeamView(Long id, String name, String shortName) {
    }

    public record MatchSummary(
            Long id,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String venue,
            Integer status,
            String homeTeamName,
            String awayTeamName) {
    }

    public record InviteView(
            Integer type,
            TeamView team,
            MatchSummary match,
            UserView inviter,
            LocalDateTime expiresAt,
            Integer maxUses,
            Integer usedCount,
            Integer remainingUses,
            Integer status,
            String statusLabel,
            boolean exhausted) {
    }

    public record InviteAdminView(
            Long id,
            Integer type,
            String typeLabel,
            TeamView team,
            MatchSummary match,
            UserView inviter,
            LocalDateTime expiresAt,
            Integer maxUses,
            Integer usedCount,
            Integer remainingUses,
            Integer status,
            String statusLabel,
            String inviteUrl,
            LocalDateTime createdAt) {
    }
}
