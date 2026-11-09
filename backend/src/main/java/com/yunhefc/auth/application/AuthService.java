package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.InviteLink;
import com.yunhefc.auth.domain.InviteType;
import com.yunhefc.auth.domain.RoleCodes;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.dto.AuthDtos.AuthResult;
import com.yunhefc.auth.dto.AuthDtos.LoginRequest;
import com.yunhefc.auth.dto.AuthDtos.MeView;
import com.yunhefc.auth.dto.AuthDtos.PlayerView;
import com.yunhefc.auth.dto.AuthDtos.RegisterRequest;
import com.yunhefc.auth.dto.AuthDtos.ResetPasswordRequest;
import com.yunhefc.auth.dto.AuthDtos.UserSummary;
import com.yunhefc.auth.dto.AuthDtos.UserView;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.security.BackendPermissions;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.notification.NotificationReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccessPort;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuthService implements UserAccessPort {

    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final TeamMapper teamMapper;
    private final PlayerReadPort playerReadPort;
    private final PlayerProfileApplicationService playerProfileService;
    private final NotificationReadPort notificationReadPort;
    private final SmsCodeService smsCodeService;
    private final LoginAttemptService loginAttemptService;
    private final InviteService inviteService;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final UserViewAssembler userViewAssembler;
    private final Clock clock;

    public AuthService(
            AppUserMapper userMapper,
            AppRoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            TeamMapper teamMapper,
            PlayerReadPort playerReadPort,
            PlayerProfileApplicationService playerProfileService,
            NotificationReadPort notificationReadPort,
            SmsCodeService smsCodeService,
            LoginAttemptService loginAttemptService,
            InviteService inviteService,
            PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder,
            UserViewAssembler userViewAssembler,
            Clock clock) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.teamMapper = teamMapper;
        this.playerReadPort = playerReadPort;
        this.playerProfileService = playerProfileService;
        this.notificationReadPort = notificationReadPort;
        this.smsCodeService = smsCodeService;
        this.loginAttemptService = loginAttemptService;
        this.inviteService = inviteService;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
        this.userViewAssembler = userViewAssembler;
        this.clock = clock;
    }

    public int sendSms(String phone, String purpose) {
        validatePhone(phone);
        return smsCodeService.send(phone, purpose);
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        validatePhone(request.phone());
        validatePassword(request.password());
        if (request.position() == null || request.position() < 0 || request.position() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "位置必须为 0–3");
        }
        smsCodeService.verify(request.phone(), "REGISTER", request.code());
        if (userMapper.selectCount(new LambdaQueryWrapper<AppUser>().eq(AppUser::getPhone, request.phone())) > 0) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }

        InviteLink invite = request.inviteToken() == null || request.inviteToken().isBlank()
                ? null
                : inviteService.findUsableForRegistration(request.inviteToken());
        boolean teamInvite = invite != null && invite.getType() == InviteType.TEAM;

        Long teamId = teamId();
        Instant now = Instant.now(clock);
        AppUser user = new AppUser();
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(teamInvite ? UserStatus.ACTIVE : UserStatus.PENDING);
        user.setLastLoginAt(now);
        user.setCreatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }

        PlayerSnapshot player = playerProfileService.createProfile(
                teamId, user.getId(), request.name(),
                teamInvite ? PlayerType.PLAYER : PlayerType.GUEST, Position.of(request.position()));

        String roleCode = teamInvite ? RoleCodes.PLAYER : RoleCodes.GUEST;
        AppRole role = role(teamId, roleCode);
        if (role == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "默认角色未初始化");
        }
        userRoleMapper.upsert(user.getId(), role.getId());
        if (invite != null) {
            inviteService.consume(invite, user.getId());
        }
        auditRecorder.record(teamId, user.getId(), request.name(), "ACCOUNT_REGISTER", "USER",
                user.getId(), null, java.util.Map.of("status", user.getStatus().code(), "role", roleCode));
        return new AuthResult(userView(user), playerView(player), permissions(user.getId()));
    }

    public AuthResult login(LoginRequest request, boolean backend) {
        validatePhone(request.phone());
        loginAttemptService.check(request.phone());
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getPhone, request.phone()));
        if (user == null || user.getPasswordHash() == null) {
            loginAttemptService.recordFailure(request.phone());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(request.phone());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttemptService.clear(request.phone());

        Set<String> permissions = permissions(user.getId());
        if (backend && user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING);
        }
        if (backend && !BackendPermissions.hasBackendPermission(permissions)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号没有后台权限");
        }
        Instant lastLoginAt = Instant.now(clock);
        user.setLastLoginAt(lastLoginAt);
        userMapper.updateLastLoginAt(user.getId(), lastLoginAt);
        return new AuthResult(userView(user), player(user.getId()), permissions);
    }

    public MeView me(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return new MeView(
                userView(user),
                player(userId),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(userId)),
                permissions(userId),
                notificationReadPort.countUnread(userId));
    }

    public AuthResult meResult(Long userId) {
        MeView me = me(userId);
        return new AuthResult(me.user(), me.player(), me.permissions());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePhone(request.phone());
        validatePassword(request.newPassword());
        smsCodeService.verify(request.phone(), "RESET", request.code());
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getPhone, request.phone()));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        String passwordHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(passwordHash);
        userMapper.updatePasswordHash(user.getId(), passwordHash);
        PlayerSnapshot player = playerByUserId(user.getId());
        auditRecorder.record(player == null ? null : player.teamId(), user.getId(),
                player == null ? UserViewAssembler.maskPhone(user.getPhone()) : player.name(),
                "PASSWORD_RESET", "USER", user.getId(), null, null);
    }

    public PageResponse<UserSummary> listUsers(Integer status, long page, long pageSize) {
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<AppUser>()
                .eq(status != null, AppUser::getStatus, status)
                .orderByAsc(AppUser::getId);
        Page<AppUser> result = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<UserSummary> items = result.getRecords().stream().map(user -> {
            PlayerView player = player(user.getId());
            return new UserSummary(
                    user.getId(),
                    player == null ? null : player.name(),
                    UserViewAssembler.maskPhone(user.getPhone()),
                    user.getStatus().code(),
                    user.getStatus().label(),
                    new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())),
                    player == null ? null : player.type(),
                    player == null ? null : player.typeLabel());
        }).toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

    @Transactional
    public UserView changePhone(Long userId, String phone) {
        validatePhone(phone);
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getPhone, phone)
                .ne(AppUser::getId, userId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        String before = user.getPhone();
        user.setPhone(phone);
        try {
            userMapper.updatePhone(userId, phone);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PHONE_EXISTS);
        }
        auditRecorder.recordCurrent("PHONE_CHANGE", "USER", userId,
                java.util.Map.of("phone", UserViewAssembler.maskPhone(before)),
                java.util.Map.of("phone", UserViewAssembler.maskPhone(phone)));
        return userView(user);
    }

    @Transactional
    public void unbindWechat(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String before = user.getOpenid();
        userMapper.clearOpenid(userId);
        auditRecorder.recordCurrent("WECHAT_UNBIND", "USER", userId,
                java.util.Map.of("openid", before == null ? "" : "***"), null);
    }

    @Override
    public Optional<AuthPrincipal> load(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == UserStatus.DISABLED) {
            return Optional.empty();
        }
        PlayerSnapshot player = playerReadPort.findByUserId(userId).orElse(null);
        return Optional.of(new AuthPrincipal(
                user.getId(),
                user.getPhone(),
                user.getStatus().code(),
                player == null ? teamId() : player.teamId(),
                player == null ? UserViewAssembler.maskPhone(user.getPhone()) : player.name(),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(userId)),
                permissions(userId)));
    }

    public List<String> sections(Set<String> permissions) {
        List<String> sections = new ArrayList<>();
        if (!permissions.isEmpty()) {
            sections.add("dashboard");
        }
        addSection(sections, permissions, "matches", "match:view:any");
        addSection(sections, permissions, "registrations", "attendance:view:any");
        addSection(sections, permissions, "events", "event:edit:any");
        addSection(sections, permissions, "lineup", "lineup:view:any");
        addSection(sections, permissions, "grouping", "grouping:view:any");
        addSection(sections, permissions, "players", "player:view:any");
        addSection(sections, permissions, "roles", "role:view:any");
        addSection(sections, permissions, "settings", "settings:view:any");
        addSection(sections, permissions, "audit", "audit:view:any");
        addSection(sections, permissions, "invites", "invite:manage:any");
        return sections;
    }

    private void addSection(List<String> sections, Set<String> permissions, String section, String permission) {
        if (permissions.contains(permission)) {
            sections.add(section);
        }
    }

    private Set<String> permissions(Long userId) {
        return new LinkedHashSet<>(userRoleMapper.findPermissionCodes(userId));
    }

    private Long teamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().orderByAsc(Team::getId).last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        return team.getId();
    }

    private AppRole role(Long teamId, String code) {
        return roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getTeamId, teamId)
                .eq(AppRole::getCode, code));
    }

    private PlayerView player(Long userId) {
        PlayerSnapshot player = playerByUserId(userId);
        return player == null ? null : playerView(player);
    }

    private PlayerSnapshot playerByUserId(Long userId) {
        return playerReadPort.findByUserId(userId).orElse(null);
    }

    private UserView userView(AppUser user) {
        return userViewAssembler.toView(user);
    }

    private PlayerView playerView(PlayerSnapshot player) {
        return new PlayerView(
                player.id(),
                player.name(),
                player.jersey(),
                player.type(),
                PlayerType.of(player.type()).label(),
                player.position(),
                Position.of(player.position()).label(),
                player.skill());
    }

    private void validatePhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "手机号格式不正确");
        }
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD.matcher(password).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "密码需 8–64 位且同时包含字母和数字");
        }
    }
}
