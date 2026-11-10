package com.yunhefc.contracts.security;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 账号状态与角色只读端口；由 auth 实现，避免业务域直接依赖 auth。
 */
public interface UserAccountReadPort {

    Optional<UserAccountSnapshot> find(Long userId);

    Map<Long, UserAccountSnapshot> findByIds(Collection<Long> userIds);

    /**
     * 按账号状态与管理员身份筛选用户 ID；admin 为 null 表示不筛选。
     * 管理员指拥有默认 ADMIN 角色或含 role:manage:any 权限的角色。
     */
    Set<Long> findUserIds(Integer status, Boolean admin);
}
