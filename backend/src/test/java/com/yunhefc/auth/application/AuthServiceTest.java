package com.yunhefc.auth.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.dto.AuthDtos.LoginRequest;
import com.yunhefc.auth.dto.AuthDtos.RegisterRequest;
import com.yunhefc.auth.dto.AuthDtos.ResetPasswordRequest;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.notification.NotificationReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserMapper userMapper;
    @Mock
    private AppRoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private TeamMapper teamMapper;
    @Mock
    private PlayerReadPort playerReadPort;
    @Mock
    private PlayerProfileApplicationService playerProfileService;
    @Mock
    private NotificationReadPort notificationReadPort;
    @Mock
    private SmsCodeService smsCodeService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private InviteService inviteService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private UserViewAssembler userViewAssembler;
    @Mock
    private Clock clock;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerMapsDuplicatePhoneToConflict() {
        RegisterRequest request = new RegisterRequest("13900000001", "123456", "Passw0rd!", "球员", 0, null);
        Team team = new Team();
        team.setId(1L);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hash");
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(teamMapper.selectOne(any())).thenReturn(team);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-10T00:00:00Z"));
        when(userMapper.insert(any(AppUser.class)))
                .thenThrow(new DuplicateKeyException(
                        "Duplicate entry '13900000001' for key 'app_user.uk_user_phone'"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_EXISTS);
    }

    @Test
    void changePhoneMapsUniqueRaceToConflict() {
        AppUser user = user(7L, "13900000001");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.updatePhone(7L, "13900000002"))
                .thenThrow(new DuplicateKeyException(
                        "Duplicate entry '13900000002' for key 'app_user.uk_user_phone'"));

        assertThatThrownBy(() -> authService.changePhone(7L, "13900000002"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_EXISTS);
    }

    @Test
    void changePhoneUpdatesOnlyPhoneColumn() {
        AppUser user = user(7L, "13900000001");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(userMapper.selectCount(any())).thenReturn(0L);

        authService.changePhone(7L, "13900000002");

        verify(userMapper).updatePhone(7L, "13900000002");
        verify(userMapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void loginRecordsFailureForUnknownPhone() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(new LoginRequest("13900000009", "Wrong123!"), false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CREDENTIALS);

        verify(loginAttemptService).recordFailure("13900000009");
    }

    @Test
    void loginUpdatesOnlyLastLoginColumn() {
        AppUser user = user(7L, "13900000001");
        user.setPasswordHash("hash");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("Passw0rd!", "hash")).thenReturn(true);
        when(userRoleMapper.findPermissionCodes(7L)).thenReturn(List.of("registration:create:own"));
        when(playerReadPort.findByUserId(7L)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(Instant.parse("2026-11-10T00:00:00Z"));

        authService.login(new LoginRequest("13900000001", "Passw0rd!"), false);

        verify(userMapper).updateLastLoginAt(eq(7L), any());
        verify(userMapper, never()).updateById(any(AppUser.class));
    }

    @Test
    void resetPasswordUpdatesOnlyPasswordHashColumn() {
        AppUser user = user(7L, "13900000001");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode("NewPassw0rd!")).thenReturn("new-hash");
        when(playerReadPort.findByUserId(7L)).thenReturn(Optional.empty());

        authService.resetPassword(new ResetPasswordRequest("13900000001", "123456", "NewPassw0rd!"));

        verify(userMapper).updatePasswordHash(7L, "new-hash");
        verify(userMapper, never()).updateById(any(AppUser.class));
    }

    private AppUser user(Long id, String phone) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setPhone(phone);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
