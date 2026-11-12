package com.yunhefc.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.wechat.WechatOAuthClient;
import com.yunhefc.auth.wechat.WechatOAuthException;
import com.yunhefc.auth.wechat.WechatProperties;
import com.yunhefc.auth.wechat.WechatStateStore;
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
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@ExtendWith(MockitoExtension.class)
class WechatAuthServiceTest {

    @Mock
    private WechatProperties properties;
    @Mock
    private WechatOAuthClient oauthClient;
    @Mock
    private WechatStateStore stateStore;
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
    private SmsCodeService smsCodeService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuditRecorder auditRecorder;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private AuthSessionService sessionService;
    @Mock
    private UserViewAssembler userViewAssembler;
    @Mock
    private AvatarUrlSigner avatarUrlSigner;
    @Mock
    private Clock clock;

    @InjectMocks
    private WechatAuthService service;

    @Test
    void nonWechatUserAgentReturns42201WithPrompt() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isRequireWechatUa()).thenReturn(true);

        assertThatThrownBy(() -> service.authorize("/", "Mozilla/5.0", mock(HttpServletRequest.class)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
                    assertThat(ex.getMessage()).isEqualTo("请在微信内打开");
                });
    }

    @Test
    void callbackBlankCodeReturns40001WithoutCallingProvider() {
        assertThatThrownBy(() -> service.callback(" ", "state-1", mock(HttpServletRequest.class),
                mock(HttpServletResponse.class)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void callbackMapsInvalidCodeTo40001() {
        HttpServletRequest request = callbackRequest();
        when(oauthClient.exchange("code-1"))
                .thenThrow(new WechatOAuthException(WechatOAuthException.Reason.INVALID_CODE));

        assertThatThrownBy(() -> service.callback("code-1", "state-1", request,
                mock(HttpServletResponse.class)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void callbackMapsProviderErrorTo50001() {
        HttpServletRequest request = callbackRequest();
        when(oauthClient.exchange("code-1"))
                .thenThrow(new WechatOAuthException(WechatOAuthException.Reason.PROVIDER_ERROR));

        assertThatThrownBy(() -> service.callback("code-1", "state-1", request,
                mock(HttpServletResponse.class)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    @Test
    void callbackBlankOpenidMapsTo50001() {
        HttpServletRequest request = callbackRequest();
        when(oauthClient.exchange("code-1")).thenReturn(new WechatOAuthClient.WechatUser(" "));

        assertThatThrownBy(() -> service.callback("code-1", "state-1", request,
                mock(HttpServletResponse.class)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR));
    }

    private HttpServletRequest callbackRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(SessionKeys.WECHAT_STATE)).thenReturn("state-1");
        when(stateStore.consume("state-1")).thenReturn("/");
        return request;
    }
}
