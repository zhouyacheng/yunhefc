package com.yunhefc.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.domain.RegistrationType;
import com.yunhefc.registration.dto.RegistrationDtos.CancellationResult;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

class RegistrationApplicationServiceTest {

    private static final long MATCH_ID = 1L;
    private static final long TEAM_ID = 2L;
    private static final long USER_ID = 3L;
    private static final long PLAYER_ID = 4L;
    private static final long PROMOTED_PLAYER_ID = 5L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final RegistrationMapper registrationMapper = mock(RegistrationMapper.class);
    private final MatchRegistrationContextPort matchPort = mock(MatchRegistrationContextPort.class);
    private final PlayerReadPort playerReadPort = mock(PlayerReadPort.class);
    private final UserAccountReadPort userAccountReadPort = mock(UserAccountReadPort.class);
    private final RegistrationCancellationService cancellationService =
            mock(RegistrationCancellationService.class);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final RegistrationApplicationService service = new RegistrationApplicationService(
            registrationMapper, matchPort, playerReadPort, userAccountReadPort, cancellationService,
            outboxService, Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uniqueKeyConflictIsConvertedToDuplicateRegistration() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID)).thenReturn(Optional.of(true));
        when(matchPort.lockForRegistration(MATCH_ID, TEAM_ID)).thenReturn(Optional.of(openMatch()));
        when(playerReadPort.findByUserId(USER_ID)).thenReturn(Optional.of(formalPlayer()));
        when(registrationMapper.countActiveRegistration(MATCH_ID, PLAYER_ID)).thenReturn(0);
        when(registrationMapper.countByTypeAndStatus(any(), anyInt(), anyInt())).thenReturn(0);
        doThrow(new DuplicateKeyException("uk_registration_active"))
                .when(registrationMapper).insert(any(Registration.class));

        assertThatThrownBy(() -> service.register(MATCH_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_ACTIVE_REGISTRATION);
    }

    @Test
    void registrationRejectsAccountThatBecameDisabled() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID))
                .thenReturn(Optional.of(false));

        assertThatThrownBy(() -> service.register(MATCH_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void cancelConfirmedRegistrationReturnsPromotedPlayer() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID)).thenReturn(Optional.of(true));
        when(matchPort.lockForRegistration(MATCH_ID, TEAM_ID)).thenReturn(Optional.of(openMatch()));
        when(playerReadPort.findByUserId(USER_ID)).thenReturn(Optional.of(formalPlayer()));
        Registration confirmed = registration(RegistrationStatus.CONFIRMED, RegistrationType.PLAYER);
        when(registrationMapper.selectOne(any())).thenReturn(confirmed);
        when(cancellationService.cancelAndPromote(confirmed, USER_ID, NOW, openMatch()))
                .thenReturn(Optional.of(PROMOTED_PLAYER_ID));

        CancellationResult result = service.cancelMyRegistration(MATCH_ID);

        assertThat(result.cancelled()).isTrue();
        assertThat(result.promoted()).isTrue();
        assertThat(result.promotedPlayerId()).isEqualTo(PROMOTED_PLAYER_ID);
    }

    @Test
    void cancelWithoutPromotionReportsNoPromotedPlayer() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID)).thenReturn(Optional.of(true));
        when(matchPort.lockForRegistration(MATCH_ID, TEAM_ID)).thenReturn(Optional.of(openMatch()));
        when(playerReadPort.findByUserId(USER_ID)).thenReturn(Optional.of(formalPlayer()));
        Registration waitlisted = registration(RegistrationStatus.WAITLISTED, RegistrationType.PLAYER);
        when(registrationMapper.selectOne(any())).thenReturn(waitlisted);
        when(cancellationService.cancelAndPromote(waitlisted, USER_ID, NOW, openMatch()))
                .thenReturn(Optional.empty());

        CancellationResult result = service.cancelMyRegistration(MATCH_ID);

        assertThat(result.cancelled()).isTrue();
        assertThat(result.promoted()).isFalse();
        assertThat(result.promotedPlayerId()).isNull();
    }

    @Test
    void cancelIsRejectedAfterLock() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID)).thenReturn(Optional.of(true));
        when(matchPort.lockForRegistration(MATCH_ID, TEAM_ID))
                .thenReturn(Optional.of(matchAt(NOW, NOW.minusSeconds(1), 2)));

        assertThatThrownBy(() -> service.cancelMyRegistration(MATCH_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.MATCH_STATE_CONFLICT);
    }

    @Test
    void cancelIsRejectedForCancelledOrPostponedMatch() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID)).thenReturn(Optional.of(true));
        when(matchPort.lockForRegistration(MATCH_ID, TEAM_ID))
                .thenReturn(Optional.of(matchAt(NOW, NOW.plusSeconds(3_600), 6)));

        assertThatThrownBy(() -> service.cancelMyRegistration(MATCH_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.MATCH_STATE_CONFLICT);
    }

    @Test
    void cancelWithoutActiveRegistrationIsNotFound() {
        authenticate();
        when(userAccountReadPort.lockActiveForRegistration(USER_ID)).thenReturn(Optional.of(true));
        when(matchPort.lockForRegistration(MATCH_ID, TEAM_ID)).thenReturn(Optional.of(openMatch()));
        when(playerReadPort.findByUserId(USER_ID)).thenReturn(Optional.of(formalPlayer()));
        when(registrationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.cancelMyRegistration(MATCH_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private void authenticate() {
        AuthPrincipal principal = new AuthPrincipal(
                USER_ID, "13800000000", 1, TEAM_ID, "测试球员", Set.of("PLAYER"), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }

    private MatchSnapshot openMatch() {
        return matchAt(NOW, NOW.plusSeconds(7_200), 0);
    }

    private MatchSnapshot matchAt(Instant now, Instant cancelLock, int status) {
        return new MatchSnapshot(MATCH_ID, TEAM_ID, "测试比赛",
                now.plusSeconds(86_400), now.plusSeconds(93_600),
                now.plusSeconds(3_600), cancelLock,
                30, 4, 2, "测试场地", status, "橙队", "兰队", null, "2099", 1, null, true);
    }

    private PlayerSnapshot formalPlayer() {
        return new PlayerSnapshot(PLAYER_ID, TEAM_ID, USER_ID, "测试球员", 8, 1, 1, 3, null);
    }

    private Registration registration(RegistrationStatus status, RegistrationType type) {
        Registration registration = new Registration();
        registration.setId(20L);
        registration.setMatchId(MATCH_ID);
        registration.setPlayerId(PLAYER_ID);
        registration.setType(type);
        registration.setStatus(status);
        registration.setRegisteredAt(NOW);
        return registration;
    }
}
