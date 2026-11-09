package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppRole;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.Permission;
import com.yunhefc.auth.domain.RoleCodes;
import com.yunhefc.auth.domain.Team;
import com.yunhefc.auth.dto.RoleDtos.PermissionView;
import com.yunhefc.auth.dto.RoleDtos.RoleCreateRequest;
import com.yunhefc.auth.dto.RoleDtos.RoleView;
import com.yunhefc.auth.dto.RoleDtos.UserRolesRequest;
import com.yunhefc.auth.dto.RoleDtos.UserRolesView;
import com.yunhefc.auth.infrastructure.AppRoleMapper;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.PermissionMapper;
import com.yunhefc.auth.infrastructure.RolePermissionMapper;
import com.yunhefc.auth.infrastructure.TeamMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.infra.outbox.OutboxEvent;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RoleService {

    private static final Pattern ROLE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    private final AppRoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AppUserMapper userMapper;
    private final TeamMapper teamMapper;
    private final AuditRecorder auditRecorder;
    private final OutboxService outboxService;

    public RoleService(
            AppRoleMapper roleMapper,
            PermissionMapper permissionMapper,
            UserRoleMapper userRoleMapper,
            RolePermissionMapper rolePermissionMapper,
            AppUserMapper userMapper,
            TeamMapper teamMapper,
            AuditRecorder auditRecorder,
            OutboxService outboxService) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.auditRecorder = auditRecorder;
        this.outboxService = outboxService;
    }

    public List<RoleView> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<AppRole>().orderByAsc(AppRole::getId))
                .stream()
                .map(role -> new RoleView(
                        role.getId(),
                        role.getCode(),
                        role.getName(),
                        role.getIsDefault() == 1,
                        rolePermissionMapper.findPermissionCodes(role.getId())))
                .toList();
    }

    @Transactional
    public RoleView create(RoleCreateRequest request) {
        if (!ROLE_CODE.matcher(request.code()).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色编码格式不合法");
        }
        Long teamId = teamId();
        if (roleMapper.selectCount(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getTeamId, teamId)
                .eq(AppRole::getCode, request.code())) > 0) {
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }
        List<Permission> permissions = validatePermissions(request.permissions());
        AppRole role = new AppRole();
        role.setTeamId(teamId);
        role.setCode(request.code());
        role.setName(request.name());
        role.setIsDefault(0);
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            // 伪删除不释放 uk_role_team_code，已删除角色的 code 仍然占用
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }
        replacePermissions(role.getId(), permissions);
        RoleView view = view(role);
        audit("ROLE_CREATE", role.getId(), null, view);
        return view;
    }

    @Transactional
    public RoleView rename(Long roleId, String name) {
        AppRole role = requireRole(roleId);
        String before = role.getName();
        role.setName(name);
        roleMapper.updateById(role);
        RoleView view = view(role);
        audit("ROLE_RENAME", roleId, Map.of("name", before), view);
        return view;
    }

    @Transactional
    public void delete(Long roleId) {
        AppRole role = requireRole(roleId);
        if (role.getIsDefault() == 1) {
            throw new BusinessException(ErrorCode.DEFAULT_ROLE_PROTECTED);
        }
        if (userRoleMapper.countUsers(roleId) > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        RoleView before = view(role);
        rolePermissionMapper.removeAll(roleId);
        roleMapper.deleteById(roleId);
        audit("ROLE_DELETE", roleId, before, null);
    }

    @Transactional
    public RoleView setPermissions(Long roleId, List<String> codes) {
        AppRole role = requireRole(roleId);
        List<String> before = rolePermissionMapper.findPermissionCodes(roleId);
        List<Permission> permissions = validatePermissions(codes);
        replacePermissions(roleId, permissions);
        ensureAdminExists();
        RoleView view = view(role);
        audit("ROLE_PERMISSIONS_UPDATE", roleId, before, view);
        notifyRoleUsers(roleId, role.getCode());
        return view;
    }

    public List<PermissionView> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getCode))
                .stream()
                .map(p -> new PermissionView(p.getId(), p.getCode(), p.getName(), p.getResource(), p.getAction(), p.getScope()))
                .toList();
    }

    @Transactional
    public UserRolesView assignRoles(Long userId, UserRolesRequest request) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        List<AppRole> roles = roleMapper.selectBatchIds(request.roles());
        if (roles.size() != request.roles().size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "角色不存在");
        }
        Set<String> codes = new LinkedHashSet<>(roles.stream().map(AppRole::getCode).toList());
        if (codes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "至少分配一个角色");
        }
        if (codes.contains(RoleCodes.GUEST) && codes.contains(RoleCodes.PLAYER)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "GUEST 与 PLAYER 互斥");
        }
        if (codes.contains(RoleCodes.ADMIN) && !codes.contains(RoleCodes.PLAYER)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "管理员必须叠加正式球员角色");
        }
        List<String> before = userRoleMapper.findRoleCodes(userId);
        userRoleMapper.removeAll(userId);
        userRoleMapper.upsertBatch(userId, roles.stream().map(AppRole::getId).toList());
        ensureAdminExists();
        List<String> after = userRoleMapper.findRoleCodes(userId);
        audit("USER", "USER_ROLES_UPDATE", userId, before, after);
        outboxService.append(null, "USER", userId, EventTypes.ROLE_CHANGED,
                Map.of("userId", userId, "roles", after, "actorUserId", CurrentUser.principal().userId()));
        return new UserRolesView(userId, after);
    }

    private void audit(String action, Long targetId, Object before, Object after) {
        audit("ROLE", action, targetId, before, after);
    }

    private void audit(String targetType, String action, Long targetId, Object before, Object after) {
        auditRecorder.recordCurrent(action, targetType, targetId, before, after);
    }

    private void notifyRoleUsers(Long roleId, String roleCode) {
        Long actorId = CurrentUser.principal().userId();
        List<OutboxEvent> events = userRoleMapper.findActiveUserIdsByRole(roleId).stream()
                .map(userId -> outboxService.newEvent(null, "USER", userId, EventTypes.ROLE_CHANGED,
                        Map.of("userId", userId, "roles", List.of(roleCode), "actorUserId", actorId)))
                .toList();
        outboxService.appendBatch(events);
    }

    private void ensureAdminExists() {
        if (rolePermissionMapper.countActiveUsersWithRoleManage() == 0) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
        }
    }

    private List<Permission> validatePermissions(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<Permission> permissions = permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .in(Permission::getCode, codes));
        if (permissions.size() != new LinkedHashSet<>(codes).size()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "存在未知权限码");
        }
        return new ArrayList<>(permissions);
    }

    private void replacePermissions(Long roleId, List<Permission> permissions) {
        rolePermissionMapper.removeAll(roleId);
        if (!permissions.isEmpty()) {
            rolePermissionMapper.upsertBatch(roleId,
                    permissions.stream().map(Permission::getId).toList());
        }
    }

    private AppRole requireRole(Long roleId) {
        AppRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private RoleView view(AppRole role) {
        return new RoleView(role.getId(), role.getCode(), role.getName(), role.getIsDefault() == 1,
                rolePermissionMapper.findPermissionCodes(role.getId()));
    }

    private Long teamId() {
        Team team = teamMapper.selectOne(new LambdaQueryWrapper<Team>().orderByAsc(Team::getId).last("LIMIT 1"));
        if (team == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "球队未初始化");
        }
        return team.getId();
    }
}
