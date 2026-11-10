package com.yunhefc.registration.application;

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
import com.yunhefc.registration.domain.Registration;
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

    private final RegistrationMapper registrationMapper = mock(RegistrationMapper.class);
    private final MatchRegistrationContextPort matchPort = mock(MatchRegistrationContextPort.class);
    private final PlayerReadPort playerReadPort = mock(PlayerReadPort.class);
    private final RegistrationApplicationService service = new RegistrationApplicationService(
            registrationMapper, matchPort, playerReadPort,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uniqueKeyConflictIsConvertedToDuplicateRegistration() {
        authenticate();
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

    private void authenticate() {
        AuthPrincipal principal = new AuthPrincipal(
                USER_ID, "13800000000", 1, TEAM_ID, "测试球员", Set.of("PLAYER"), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }

    private MatchSnapshot openMatch() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new MatchSnapshot(MATCH_ID, TEAM_ID, "测试比赛",
                now.plusSeconds(86_400), now.plusSeconds(93_600),
                now.plusSeconds(3_600), now.plusSeconds(7_200),
                30, 4, 2, "测试场地", 0, "橙队", "兰队");
    }

    private PlayerSnapshot formalPlayer() {
        return new PlayerSnapshot(PLAYER_ID, TEAM_ID, USER_ID, "测试球员", 8, 1, 1, 3);
    }
}
