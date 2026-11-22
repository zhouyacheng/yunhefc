package com.yunhefc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.dto.MiniDtos.MiniBindPhoneRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginResponse;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.MiniBindTicketStore;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.miniprogram.MiniProgramClient;
import com.yunhefc.auth.miniprogram.MiniProgramProperties;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.AuthSessionService;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.security.SessionKeys;
import com.yunhefc.infra.storage.AvatarUrlSigner;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

class MiniAuthServiceTest {

    private final MiniProgramProperties properties = new MiniProgramProperties();
    private final MiniProgramClient client = mock(MiniProgramClient.class);
    private final AppUserMapper userMapper = mock(AppUserMapper.class);
    private final AppRoleMapper roleMapper = mock(AppRoleMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final TeamMapper teamMapper = mock(TeamMapper.class);
    private final PlayerReadPort playerReadPort = mock(PlayerReadPort.class);
    private final PlayerProfileApplicationService playerProfileService = mock(PlayerProfileApplicationService.class);
    private final SmsCodeService smsCodeService = mock(SmsCodeService.class);
    private final MiniBindTicketStore bindTicketStore = mock(MiniBindTicketStore.class);
    private final LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final AuthSessionService sessionService = mock(AuthSessionService.class);
    private final UserViewAssembler userViewAssembler = mock(UserViewAssembler.class);
    private final AvatarUrlSigner avatarUrlSigner = mock(AvatarUrlSigner.class);

    private final MiniAuthService service = new MiniAuthService(properties, client, userMapper, roleMapper,
            userRoleMapper, teamMapper, playerReadPort, playerProfileService, smsCodeService,
            bindTicketStore, loginAttemptService, auditRecorder, sessionService, userViewAssembler,
            avatarUrlSigner);

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
    }

    @Test
    void unboundLoginIssuesOneTimeTicket() {
        when(client.exchange("code-1")).thenReturn(new MiniProgramClient.WechatSession("openid-1"));
        when(userMapper.selectOne(any())).thenReturn(null);
        when(bindTicketStore.save("openid-1")).thenReturn("ticket-1");

        MiniLoginResponse result = service.login(new MiniLoginRequest("code-1"), request, response);

        assertThat(result.needBindPhone()).isTrue();
        assertThat(result.bindTicket()).isEqualTo("ticket-1");
        verify(bindTicketStore).save("openid-1");
    }

    @Test
    void boundLoginEstablishesSessionAndUpdatesLastLogin() {
        AppUser user = user(1L, "13900000001", UserStatus.ACTIVE);
        user.setMpOpenid("openid-1");
        when(client.exchange("code-1")).thenReturn(new MiniProgramClient.WechatSession("openid-1"));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(playerReadPort.findByUserId(1L)).thenReturn(java.util.Optional.empty());
        when(userRoleMapper.findPermissionCodes(1L)).thenReturn(java.util.List.of());
        when(sessionService.establish(eq(request), eq(response), eq(SessionKeys.C_USER_ID), eq(1L)))
                .thenReturn("csrf-1");
        when(userViewAssembler.toView(user)).thenReturn(new UserView(1L, "球员", "139****0001", 1,
                "ACTIVE", Set.of(), LocalDateTime.now(), true));

        var result = service.login(new MiniLoginRequest("code-1"), request, response);

        assertThat(result.needBindPhone()).isFalse();
        assertThat(result.csrfToken()).isEqualTo("csrf-1");
        verify(userMapper).updateLastLoginAt(eq(1L), any());
        verify(loginAttemptService).clear("13900000001");
    }

    @Test
    void newPhoneWithoutNameIsRejected() {
        when(bindTicketStore.consume("ticket-1")).thenReturn("openid-1");
        when(userMapper.selectOne(any())).thenReturn(null);

        MiniBindPhoneRequest payload = new MiniBindPhoneRequest("ticket-1", "13900000002", "123456", null, null);

        assertThatThrownBy(() -> service.bindPhone(payload, request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void disabledAccountCannotBind() {
        when(bindTicketStore.consume("ticket-1")).thenReturn("openid-1");
        when(userMapper.selectOne(any())).thenReturn(user(2L, "13900000003", UserStatus.DISABLED));

        MiniBindPhoneRequest payload = new MiniBindPhoneRequest("ticket-1", "13900000003", "123456", null, null);

        assertThatThrownBy(() -> service.bindPhone(payload, request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.ACCOUNT_DISABLED);
    }

    @Test
    void consumedOrExpiredTicketIsRejected() {
        when(bindTicketStore.consume("ticket-1")).thenReturn(null);

        MiniBindPhoneRequest payload = new MiniBindPhoneRequest("ticket-1", "13900000004", "123456", null, null);

        assertThatThrownBy(() -> service.bindPhone(payload, request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void disabledFeatureIsRejected() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.login(new MiniLoginRequest("code-1"), request, response))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    private static AppUser user(Long id, String phone, UserStatus status) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setPhone(phone);
        user.setStatus(status);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
