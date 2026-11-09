package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.InviteLink;
import com.yunhefc.auth.domain.Team;
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
import com.yunhefc.common.Labels;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccessPort;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final PlayerMapper playerMapper;
    private final NotificationMapper notificationMapper;
    private final SmsCodeService smsCodeService;
    private final LoginAttemptService loginAttemptService;
    private final InviteService inviteService;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    public AuthService(
            AppUserMapper userMapper,
            AppRoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            TeamMapper teamMapper,
            PlayerMapper playerMapper,
            NotificationMapper notificationMapper,
            SmsCodeService smsCodeService,
            LoginAttemptService loginAttemptService,
            InviteService inviteService,
            PasswordEncoder passwordEncoder,
            AuditRecorder auditRecorder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.notificationMapper = notificationMapper;
        this.smsCodeService = smsCodeService;
        this.loginAttemptService = loginAttemptService;
        this.inviteService = inviteService;
        this.passwordEncoder = passwordEncoder;
        this.auditRecorder = auditRecorder;
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
        boolean teamInvite = invite != null && invite.getType() == 0;

        Long teamId = teamId();
        AppUser user = new AppUser();
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(teamInvite ? 1 : 0);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);

        Player player = new Player();
        player.setTeamId(teamId);
        player.setUserId(user.getId());
        player.setName(request.name());
        player.setType(teamInvite ? 1 : 0);
        player.setPosition(request.position());
        player.setSecondaryPositions("[]");
        player.setSkill(3);
        playerMapper.insert(player);

        String roleCode = teamInvite ? "PLAYER" : "GUEST";
        AppRole role = role(teamId, roleCode);
        if (role == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "默认角色未初始化");
        }
        userRoleMapper.upsert(user.getId(), role.getId());
        if (invite != null) {
            inviteService.consume(invite, user.getId());
        }
        auditRecorder.record(user.getId(), "ACCOUNT_REGISTER", "USER", user.getId(), null,
                java.util.Map.of("status", user.getStatus(), "role", roleCode));
        return new AuthResult(userView(user), playerView(player), permissions(user.getId()));
    }

    public AuthResult login(LoginRequest request, boolean backend) {
        validatePhone(request.phone());
        loginAttemptService.check(request.phone());
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getPhone, request.phone()));
        if (user == null || user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(request.phone());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttemptService.clear(request.phone());

        Set<String> permissions = permissions(user.getId());
        if (backend && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_PENDING);
        }
        if (backend && permissions.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号没有后台权限");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
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
                notificationMapper.countUnread(userId));
    }

    public AuthResult meResult(Long userId) {
        MeView me = me(userId);
        return new AuthResult(me.user(), me.player(), me.permissions());
    }

    public void resetPassword(ResetPasswordRequest request) {
        validatePhone(request.phone());
        validatePassword(request.newPassword());
        smsCodeService.verify(request.phone(), "RESET", request.code());
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getPhone, request.phone()));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        if (user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        auditRecorder.record(user.getId(), "PASSWORD_RESET", "USER", user.getId(), null, null);
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
                    maskPhone(user.getPhone()),
                    user.getStatus(),
                    Labels.userStatus(user.getStatus()),
                    new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())),
                    player == null ? null : player.type(),
                    player == null ? null : player.typeLabel());
        }).toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

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
        userMapper.updateById(user);
        auditRecorder.record(CurrentUser.principal().userId(), "PHONE_CHANGE", "USER", userId,
                java.util.Map.of("phone", before), java.util.Map.of("phone", phone));
        return userView(user);
    }

    public void unbindWechat(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        String before = user.getOpenid();
        user.setOpenid(null);
        userMapper.updateById(user);
        auditRecorder.record(CurrentUser.principal().userId(), "WECHAT_UNBIND", "USER", userId,
                java.util.Map.of("openid", before == null ? "" : before), null);
    }

    @Override
    public Optional<AuthPrincipal> load(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 2) {
            return Optional.empty();
        }
        return Optional.of(new AuthPrincipal(
                user.getId(),
                user.getPhone(),
                user.getStatus(),
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
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUserId, userId)
                .last("LIMIT 1"));
        return player == null ? null : playerView(player);
    }

    private UserView userView(AppUser user) {
        return new UserView(
                user.getId(),
                user.getPhone(),
                user.getStatus(),
                Labels.userStatus(user.getStatus()),
                new LinkedHashSet<>(userRoleMapper.findRoleCodes(user.getId())));
    }

    private PlayerView playerView(Player player) {
        return new PlayerView(
                player.getId(),
                player.getName(),
                player.getJersey(),
                player.getType(),
                Labels.playerType(player.getType()),
                player.getPosition(),
                Labels.position(player.getPosition()),
                player.getSkill());
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

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
