package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.AuthDtos.PlayerView;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.dto.WechatDtos.AuthorizeResponse;
import com.yunhefc.auth.dto.WechatDtos.BindPhoneRequest;
import com.yunhefc.auth.dto.WechatDtos.CallbackResponse;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.wechat.WechatOAuthClient;
import com.yunhefc.auth.wechat.WechatProperties;
import com.yunhefc.auth.wechat.WechatStateStore;
import com.yunhefc.common.Labels;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.AuthSessionService;
import com.yunhefc.contracts.security.SessionKeys;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WechatAuthService {

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    private final WechatProperties properties;
    private final WechatOAuthClient oauthClient;
    private final WechatStateStore stateStore;
    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final SmsCodeService smsCodeService;
    private final AuthSessionService sessionService;

    public WechatAuthService(
            WechatProperties properties,
            WechatOAuthClient oauthClient,
            WechatStateStore stateStore,
            AppUserMapper userMapper,
            AppRoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            TeamMapper teamMapper,
            PlayerMapper playerMapper,
            SmsCodeService smsCodeService,
            AuthSessionService sessionService) {
        this.properties = properties;
        this.oauthClient = oauthClient;
        this.stateStore = stateStore;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.smsCodeService = smsCodeService;
        this.sessionService = sessionService;
    }

    public AuthorizeResponse authorize(String redirect, String userAgent) {
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
        return new AuthorizeResponse(oauthClient.authorizeUrl(state));
    }

    public CallbackResponse callback(String code, String state, HttpServletRequest request,
                                     HttpServletResponse response) {
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
    public AuthResponse bindPhone(BindPhoneRequest request, HttpServletRequest servletRequest,
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
            if (user.getStatus() == 2) {
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
        return login(user, servletRequest, response);
    }

    private AppUser createUser(BindPhoneRequest request, String openid) {
        Long teamId = teamId();
        AppUser user = new AppUser();
        user.setPhone(request.phone());
        user.setOpenid(openid);
        user.setStatus(0);
        userMapper.insert(user);

        Player player = new Player();
        player.setTeamId(teamId);
        player.setUserId(user.getId());
        player.setName(request.name());
        player.setType(0);
        player.setPosition(request.position());
        player.setSecondaryPositions("[]");
        player.setSkill(3);
        playerMapper.insert(player);

        AppRole role = roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getTeamId, teamId)
                .eq(AppRole::getCode, "GUEST"));
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
        user.setOpenid(openid);
        userMapper.updateById(user);
    }

    private AppUser requireUser(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    private AuthResponse login(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        if (user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUserId, user.getId())
                .last("LIMIT 1"));
        Set<String> permissions = new LinkedHashSet<>(userRoleMapper.findPermissionCodes(user.getId()));
        String csrf = sessionService.establish(request, response, SessionKeys.C_USER_ID, user.getId());
        return new AuthResponse(userView(user), playerView(player), permissions, csrf);
    }

    private UserView userView(AppUser user) {
        return new UserView(user.getId(), user.getPhone(), user.getStatus(), Labels.userStatus(user.getStatus()),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())));
    }

    private PlayerView playerView(Player player) {
        if (player == null) {
            return null;
        }
        return new PlayerView(player.getId(), player.getName(), player.getJersey(), player.getType(),
                Labels.playerType(player.getType()), player.getPosition(),
                Labels.position(player.getPosition()), player.getSkill());
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
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("://")) {
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
