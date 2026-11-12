package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.RoleCodes;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.RolePermissionMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.registration.RegistrationLifecyclePort;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.player.application.PlayerAdminApplicationService;
import com.yunhefc.player.application.PlayerProfileApplicationService;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 账号生命周期编排（P-05 例外）：审核、转正、注销在 auth 应用服务内同步编排
 * 账号、球员、角色与报名；其他业务域不得复制该事务边界。
 */
@Service
@RequiredArgsConstructor
public class AccountLifecycleApplicationService {

    private final AppUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final TeamMapper teamMapper;
    private final PlayerReadPort playerReadPort;
    private final PlayerAdminApplicationService playerAdminService;
    private final PlayerProfileApplicationService playerProfileService;
    private final RegistrationLifecyclePort registrationLifecyclePort;
    private final AuditRecorder auditRecorder;
    private final OutboxService outboxService;

    /**
     * 仅 PENDING 可审核；ACTIVE 幂等返回，不重复写审计与通知；DISABLED 不可恢复。
     */
    @Transactional
    public PlayerAdminView approve(Long playerId) {
        AuthPrincipal actor = CurrentUser.principal();
        PlayerSnapshot player = requirePlayer(actor.teamId(), playerId);
        // 行锁串行化并发审核，保证审计与通知只写一次
        AppUser user = lockUser(player.userId());
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "已注销账号不能审核");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            userMapper.updateStatus(user.getId(), UserStatus.ACTIVE.code());
            auditRecorder.recordCurrent("USER_APPROVE", "USER", user.getId(),
                    Map.of("status", UserStatus.PENDING.code()),
                    Map.of("status", UserStatus.ACTIVE.code()));
            outboxService.append(actor.teamId(), "USER", user.getId(), EventTypes.ACCOUNT_APPROVED,
                    Map.of("userId", user.getId(), "actorUserId", actor.userId()));
        }
        return playerAdminService.adminView(actor.teamId(), playerId);
    }

    /**
     * PLR-08：仅 ACTIVE 外挂可转正；已正式时幂等返回。球员类型、角色与审计同事务落地。
     */
    @Transactional
    public PlayerAdminView convert(Long playerId, Integer jersey) {
        AuthPrincipal actor = CurrentUser.principal();
        PlayerSnapshot player = requirePlayer(actor.teamId(), playerId);
        if (player.type() == PlayerType.PLAYER.code()) {
            return playerAdminService.adminView(actor.teamId(), playerId);
        }
        // 行锁串行化账号状态变更，避免与注销交错
        AppUser user = lockUser(player.userId());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅正常状态的账号可转正");
        }
        Map<String, Object> before = snapshot(player, userRoleMapper.findRoleCodes(user.getId()));
        Optional<PlayerSnapshot> converted =
                playerProfileService.convertToFormal(actor.teamId(), playerId, jersey);
        if (converted.isEmpty()) {
            return playerAdminService.adminView(actor.teamId(), playerId);
        }
        replaceGuestWithPlayer(actor.teamId(), user.getId());
        List<String> roles = userRoleMapper.findRoleCodes(user.getId());
        Map<String, Object> after = snapshot(converted.get(), roles);
        auditRecorder.recordCurrent("PLAYER_CONVERT", "PLAYER", playerId, before, after);
        outboxService.append(actor.teamId(), "USER", user.getId(), EventTypes.ROLE_CHANGED,
                Map.of("userId", user.getId(), "roles", roles, "actorUserId", actor.userId()));
        return playerAdminService.adminView(actor.teamId(), playerId);
    }

    /**
     * PLR-09：注销不可恢复；已注销幂等返回。最后管理员保护与角色变更共用球队行锁。
     */
    @Transactional
    public PlayerAdminView disable(Long playerId, String reason) {
        AuthPrincipal actor = CurrentUser.principal();
        PlayerSnapshot player = requirePlayer(actor.teamId(), playerId);
        // 串行化最后管理员校验：并发注销/降权各自看到对方的旧状态会同时通过
        if (teamMapper.lockTeamId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        // 行锁串行化账号状态变更，避免与审核/转正交错
        AppUser user = lockUser(player.userId());
        if (user.getStatus() == UserStatus.DISABLED) {
            return playerAdminService.adminView(actor.teamId(), playerId);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "仅正常状态的账号可注销");
        }
        if (rolePermissionMapper.countActiveUsersWithRoleManageExcluding(user.getId()) == 0) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        userMapper.updateStatus(user.getId(), UserStatus.DISABLED.code());
        registrationLifecyclePort.cancelActiveForDisabledPlayer(
                actor.teamId(), playerId, actor.userId());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", UserStatus.DISABLED.code());
        after.put("reason", reason.strip());
        auditRecorder.recordCurrent("USER_DISABLE", "USER", user.getId(),
                Map.of("status", user.getStatus().code()), after);
        outboxService.append(actor.teamId(), "USER", user.getId(), EventTypes.ACCOUNT_DISABLED,
                Map.of("userId", user.getId(), "actorUserId", actor.userId()));
        return playerAdminService.adminView(actor.teamId(), playerId);
    }

    private AppUser lockUser(Long userId) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        return user;
    }

    private PlayerSnapshot requirePlayer(Long teamId, Long playerId) {
        return playerReadPort.findById(playerId)
                .filter(snapshot -> Objects.equals(snapshot.teamId(), teamId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在"));
    }

    private void replaceGuestWithPlayer(Long teamId, Long userId) {
        AppRole guestRole = role(teamId, RoleCodes.GUEST);
        if (guestRole != null) {
            userRoleMapper.remove(userId, guestRole.getId());
        }
        AppRole playerRole = role(teamId, RoleCodes.PLAYER);
        if (playerRole == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PLAYER 角色未初始化");
        }
        userRoleMapper.upsert(userId, playerRole.getId());
    }

    private AppRole role(Long teamId, String code) {
        return roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getTeamId, teamId)
                .eq(AppRole::getCode, code));
    }

    private Map<String, Object> snapshot(PlayerSnapshot player, List<String> roles) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("type", player.type());
        values.put("jersey", player.jersey());
        values.put("roles", roles);
        return values;
    }
}
