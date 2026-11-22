package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.RoleCodes;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.AuthDtos.PlayerView;
import com.yunhefc.auth.dto.MiniDtos.MiniBindPhoneRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginResponse;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.MiniBindTicketStore;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.miniprogram.MiniProgramClient;
import com.yunhefc.auth.miniprogram.MiniProgramException;
import com.yunhefc.auth.miniprogram.MiniProgramProperties;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.AuthSessionService;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.common.util.MaskUtils;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.security.SessionKeys;
import com.yunhefc.infra.storage.AvatarUrlSigner;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MiniAuthService {

    private static final Logger log = LoggerFactory.getLogger(MiniAuthService.class);
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    private final MiniProgramProperties properties;
    private final MiniProgramClient client;
    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMapper teamMapper;
    private final PlayerReadPort playerReadPort;
    private final PlayerProfileApplicationService playerProfileService;
    private final SmsCodeService smsCodeService;
    private final MiniBindTicketStore bindTicketStore;
    private final LoginAttemptService loginAttemptService;
    private final AuditRecorder auditRecorder;
    private final AuthSessionService sessionService;
    private final UserViewAssembler userViewAssembler;
    private final AvatarUrlSigner avatarUrlSigner;

    public MiniLoginResponse login(MiniLoginRequest request, HttpServletRequest servletRequest,
                                   HttpServletResponse response) {
        requireEnabled();
        String openid = exchange(request.code());

        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getMpOpenid, openid));
        if (user == null) {
            // 未绑定：签发 10 分钟一次性票据（Redis 只存哈希），不创建无手机号账号。
            String ticket = bindTicketStore.save(openid);
            return new MiniLoginResponse(true, ticket, null, null, null, null);
        }
        AuthResponse session = login(user, servletRequest, response);
        return new MiniLoginResponse(false, null, session.user(), session.player(),
                session.permissions(), session.csrfToken());
    }

    @Transactional
    public AuthResponse bindPhone(MiniBindPhoneRequest request, HttpServletRequest servletRequest,
                                  HttpServletResponse response) {
        requireEnabled();
        // 票据一次性：消费即失效，绑手机失败不恢复，用户需重新登录获取新票据。
        String openid = bindTicketStore.consume(request.bindTicket());
        if (openid == null || openid.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "小程序登录已过期，请重新登录");
        }
        return bindPhoneInternal(request, openid, servletRequest, response);
    }

    private AuthResponse bindPhoneInternal(MiniBindPhoneRequest request, String openid,
                                           HttpServletRequest servletRequest, HttpServletResponse response) {
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
            bindMpOpenid(user, openid);
        } else {
            if (request.name() == null || request.name().isBlank()
                    || request.position() == null || request.position() < 0 || request.position() > 3) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新账号必须提供姓名和位置");
            }
            user = createUser(request, openid);
        }
        recordWechatBind(user);
        return login(user, servletRequest, response);
    }

    private AppUser createUser(MiniBindPhoneRequest request, String openid) {
        Long teamId = teamId();
        AppUser user = new AppUser();
        user.setPhone(request.phone());
        user.setMpOpenid(openid);
        user.setStatus(UserStatus.PENDING);
        user.setCreatedAt(ShanghaiTime.now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            log.warn("小程序绑定记录唯一键冲突, phone={}", MaskUtils.maskPhone(request.phone()));
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

    private void bindMpOpenid(AppUser user, String openid) {
        AppUser other = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getMpOpenid, openid));
        if (other != null && !other.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该微信已绑定其他账号");
        }
        if (user.getMpOpenid() != null && !user.getMpOpenid().equals(openid)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "账号已绑定其他微信");
        }
        try {
            userMapper.updateMpOpenid(user.getId(), openid);
            // 同步内存实体，后续 toView 的 wechatBound 才能反映本次绑定。
            user.setMpOpenid(openid);
        } catch (DuplicateKeyException ex) {
            log.warn("小程序绑定唯一键冲突, phone={}", MaskUtils.maskPhone(user.getPhone()));
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "该微信已绑定其他账号");
        }
    }

    private void recordWechatBind(AppUser user) {
        PlayerSnapshot player = playerReadPort.findByUserId(user.getId()).orElse(null);
        Long teamId = player == null ? teamId() : player.teamId();
        String actorName = player == null
                ? UserViewAssembler.maskPhone(user.getPhone())
                : player.name();
        auditRecorder.record(teamId, user.getId(), actorName, "WECHAT_BIND", "USER", user.getId(),
                null, Map.of("openid", "***"));
    }

    private AuthResponse login(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        loginAttemptService.clear(user.getPhone());
        userMapper.updateLastLoginAt(user.getId(), ShanghaiTime.now());
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
                Position.of(player.position()).label(), player.skill(),
                avatarUrlSigner.sign(player.avatarUrl()));
    }

    private Long teamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().orderByAsc(Team::getId).last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        return team.getId();
    }

    private String exchange(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "小程序登录 code 不能为空");
        }
        MiniProgramClient.WechatSession session;
        try {
            session = client.exchange(code);
        } catch (MiniProgramException ex) {
            if (ex.reason() == MiniProgramException.Reason.INVALID_CODE) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "微信登录已失效，请重新登录");
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "微信登录失败，请稍后重试");
        }
        if (session == null || session.openid() == null || session.openid().isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "微信登录失败，请稍后重试");
        }
        return session.openid();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "小程序登录未启用");
        }
    }
}
