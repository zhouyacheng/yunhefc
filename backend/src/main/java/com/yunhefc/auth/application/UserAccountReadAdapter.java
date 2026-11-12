package com.yunhefc.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.auth.domain.AppUser;
import com.yunhefc.auth.domain.UserStatus;
import com.yunhefc.auth.infrastructure.AppUserMapper;
import com.yunhefc.auth.infrastructure.UserRoleMapper;
import com.yunhefc.auth.infrastructure.UserRoleRow;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserAccountReadAdapter implements UserAccountReadPort {

    private final AppUserMapper userMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public Optional<UserAccountSnapshot> find(Long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            return Optional.empty();
        }
        Set<String> roles = new LinkedHashSet<>(userRoleMapper.findRoleCodes(userId));
        boolean admin = !userRoleMapper.findAdminUserIds(List.of(userId)).isEmpty();
        return Optional.of(toSnapshot(user, roles, admin));
    }

    @Override
    public Optional<Boolean> lockActiveForRegistration(Long userId) {
        Integer status = userMapper.selectStatusByIdForShare(userId);
        return Optional.ofNullable(status).map(code -> code == UserStatus.ACTIVE.code());
    }

    @Override
    public Map<Long, UserAccountSnapshot> findByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = List.copyOf(userIds);
        Map<Long, Set<String>> roles = new LinkedHashMap<>();
        for (UserRoleRow row : userRoleMapper.findRoleCodesByUserIds(ids)) {
            roles.computeIfAbsent(row.getUserId(), id -> new LinkedHashSet<>()).add(row.getCode());
        }
        Set<Long> adminUserIds = new LinkedHashSet<>(userRoleMapper.findAdminUserIds(ids));
        Map<Long, UserAccountSnapshot> snapshots = new LinkedHashMap<>();
        for (AppUser user : userMapper.selectBatchIds(ids)) {
            snapshots.put(user.getId(), toSnapshot(
                    user, roles.getOrDefault(user.getId(), Set.of()), adminUserIds.contains(user.getId())));
        }
        return snapshots;
    }

    @Override
    public Set<Long> findUserIds(Integer status, Boolean admin) {
        Set<Long> userIds = new LinkedHashSet<>();
        userMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .select(AppUser::getId)
                        .eq(status != null, AppUser::getStatus, status))
                .forEach(user -> userIds.add(user.getId()));
        if (admin != null) {
            Set<Long> adminUserIds = new LinkedHashSet<>(userRoleMapper.findAdminUserIds());
            if (admin) {
                userIds.retainAll(adminUserIds);
            } else {
                userIds.removeAll(adminUserIds);
            }
        }
        return userIds;
    }

    private UserAccountSnapshot toSnapshot(AppUser user, Set<String> roles, boolean admin) {
        return new UserAccountSnapshot(
                user.getId(),
                UserViewAssembler.maskPhone(user.getPhone()),
                user.getStatus().code(),
                user.getStatus().label(),
                admin,
                roles,
                user.getCreatedAt(),
                user.isWechatBound());
    }
}
