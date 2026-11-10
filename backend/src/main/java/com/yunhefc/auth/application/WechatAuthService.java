package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.RoleCodes;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.AuthDtos.PlayerView;
import com.yunhefc.auth.dto.WechatDtos.AuthorizeResponse;
import com.yunhefc.auth.dto.WechatDtos.BindPhoneRequest;
import com.yunhefc.auth.dto.WechatDtos.BindPhoneResponse;
import com.yunhefc.auth.dto.WechatDtos.CallbackResponse;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.wechat.WechatOAuthClient;
import com.yunhefc.auth.wechat.WechatProperties;
import com.yunhefc.auth.wechat.WechatStateStore;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.AuthSessionService;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.security.SessionKeys;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WechatAuthService {

    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    private final WechatProperties properties;
    private final WechatOAuthClient oauthClient;
    private final WechatStateStore stateStore;
    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMapper teamMapper;
    private final PlayerReadPort playerReadPort;
    private final PlayerProfileApplicationService playerProfileService;
    private final SmsCodeService smsCodeService;
    private final LoginAttemptService loginAttemptService;
    private final AuthSessionService sessionService;
    private final UserViewAssembler userViewAssembler;
    private final Clock clock;

    public WechatAuthService(
            WechatProperties properties,
            WechatOAuthClient oauthClient,
            WechatStateStore stateStore,
            AppUserMapper userMapper,
            AppRoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            TeamMapper teamMapper,
            PlayerReadPort playerReadPort,
            PlayerProfileApplicationService playerProfileService,
            SmsCodeService smsCodeService,
            LoginAttemptService loginAttemptService,
            AuthSessionService sessionService,
            UserViewAssembler userViewAssembler,
            Clock clock) {
        this.properties = properties;
        this.oauthClient = oauthClient;
        this.stateStore = stateStore;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.teamMapper = teamMapper;
        this.playerReadPort = playerReadPort;
        this.playerProfileService = playerProfileService;
        this.smsCodeService = smsCodeService;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
        this.userViewAssembler = userViewAssembler;
        this.clock = clock;
    }

    public AuthorizeResponse authorize(String redirect, String userAgent, HttpServletRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "微信授权未启用");
        }
        if (properties.isRequireWechatUa()
                && (userAgent == null || !userAgent.contains("MicroMessenger"))) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅微信内可授权");
        }
        String safeRedirect = validateRedirect(redirect);
        String state = UUID.randomUUID().toString();
        stateStore.put(state, safeRedirect);
        // state 绑定发起授权的浏览器会话，防止登录 CSRF
        request.getSession(true).setAttribute(SessionKeys.WECHAT_STATE, state);
        return new AuthorizeResponse(oauthClient.authorizeUrl(state));
    }

    public CallbackResponse callback(String code, String state, HttpServletRequest request,
                                     HttpServletResponse response) {
        HttpSession stateSession = request.getSession(false);
        String expectedState = stateSession == null ? null
                : (String) stateSession.getAttribute(SessionKeys.WECHAT_STATE);
        if (expectedState == null || !expectedState.equals(state)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "微信 state 无效或已使用");
        }
        stateSession.removeAttribute(SessionKeys.WECHAT_STATE);
        String redirect = stateStore.consume(state);
        if (redirect == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "微信 state 无效或已使用");
        }
        WechatOAuthClient.WechatUser wechatUser = oauthClient.exchange(code);
        if (wechatUser.openid() == null || wechatUser.openid().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "微信授权失败");
        }

        HttpSession session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute(SessionKeys.C_USER_ID);
        if (currentUserId != null) {
            AppUser user = requireUser(currentUserId);
            bindOpenid(user, wechatUser.openid());
            return new CallbackResponse(false, redirect, login(user, request, response).csrfToken());
        }

        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getOpenid, wechatUser.openid()));
        if (user != null) {
            return new CallbackResponse(false, redirect, login(user, request, response).csrfToken());
        }

        request.getSession(true).setAttribute(SessionKeys.WECHAT_OPENID, wechatUser.openid());
        return new CallbackResponse(true, redirect, sessionService.rotateCsrf(request, response));
    }

    @Transactional
    public BindPhoneResponse bindPhone(BindPhoneRequest request, HttpServletRequest servletRequest,
                                       HttpServletResponse response) {
        HttpSession session = servletRequest.getSession(false);
        String openid = session == null ? null : (String) session.getAttribute(SessionKeys.WECHAT_OPENID);
        if (openid == null || openid.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "微信临时会话不存在");
        }
        if (request.phone() == null || !PHONE.matcher(request.phone()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "手机号格式不正确");
        }
        smsCodeService.verify(request.phone(), "BIND", request.code());

        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getPhone, request.phone()));
        if (user != null) {
            if (user.getStatus() == UserStatus.DISABLED) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
            bindOpenid(user, openid);
        } else {
            if (request.name() == null || request.name().isBlank()
                    || request.position() == null || request.position() < 0 || request.position() > 3) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新账号必须提供姓名和位置");
            }
            user = createUser(request, openid);
        }

        session.removeAttribute(SessionKeys.WECHAT_OPENID);
        AuthResponse result = login(user, servletRequest, response);
        return new BindPhoneResponse(result.user(), result.player(), result.csrfToken());
    }

    private AppUser createUser(BindPhoneRequest request, String openid) {
        Long teamId = teamId();
        AppUser user = new AppUser();
        user.setPhone(request.phone());
        user.setOpenid(openid);
        user.setStatus(UserStatus.PENDING);
        user.setCreatedAt(Instant.now(clock));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 唯一键异常文本含原始手机号/openid，只记录脱敏手机号
            log.warn("微信绑定记录唯一键冲突, phone={}", UserViewAssembler.maskPhone(request.phone()));
            String cause = ex.getMostSpecificCause().getMessage();
            if (cause != null && cause.contains("uk_user_phone")) {
                throw new BusinessException(ErrorCode.PHONE_EXISTS);
            }
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该微信已绑定其他账号");
        }

        playerProfileService.createProfile(teamId, user.getId(), request.name(),
                PlayerType.GUEST, Position.of(request.position()));

        AppRole role = roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getTeamId, teamId)
                .eq(AppRole::getCode, RoleCodes.GUEST));
        if (role == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "GUEST 角色未初始化");
        }
        userRoleMapper.upsert(user.getId(), role.getId());
        return user;
    }

    private void bindOpenid(AppUser user, String openid) {
        AppUser other = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getOpenid, openid));
        if (other != null && !other.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该微信已绑定其他账号");
        }
        if (user.getOpenid() != null && !user.getOpenid().equals(openid)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "账号已绑定其他微信");
        }
        try {
            userMapper.updateOpenid(user.getId(), openid);
        } catch (DuplicateKeyException ex) {
            log.warn("微信绑定唯一键冲突, phone={}", UserViewAssembler.maskPhone(user.getPhone()));
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该微信已绑定其他账号");
        }
    }

    private AppUser requireUser(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private AuthResponse login(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        loginAttemptService.clear(user.getPhone());
        PlayerSnapshot player = playerReadPort.findByUserId(user.getId()).orElse(null);
        Set<String> permissions = new LinkedHashSet<>(userRoleMapper.findPermissionCodes(user.getId()));
        String csrf = sessionService.establish(request, response, SessionKeys.C_USER_ID, user.getId());
        return new AuthResponse(userViewAssembler.toView(user), playerView(player), permissions, csrf);
    }

    private PlayerView playerView(PlayerSnapshot player) {
        if (player == null) {
            return null;
        }
        return new PlayerView(player.id(), player.name(), player.jersey(), player.type(),
                PlayerType.of(player.type()).label(), player.position(),
                Position.of(player.position()).label(), player.skill());
    }

    private Long teamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().orderByAsc(Team::getId).last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        return team.getId();
    }

    private String validateRedirect(String redirect) {
        String value = redirect == null || redirect.isBlank() ? "/" : redirect;
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("://")
                || value.contains("\\")
                || value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7F)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "redirect 必须是站内路径");
        }
        List<String> prefixes = properties.getAllowedRedirectPrefixes();
        if (prefixes != null && !prefixes.isEmpty()
                && prefixes.stream().noneMatch(value::startsWith)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "redirect 不在白名单");
        }
        return value;
    }
}
