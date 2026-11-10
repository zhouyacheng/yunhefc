package com.yunhefc.auth.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PrincipalMapper {

    /**
     * 一次查询取出会话鉴权所需的用户、角色与权限；球员信息由 PlayerReadPort 提供。
     */
    @Select("""
            SELECT u.id, u.phone, u.status,
                   GROUP_CONCAT(DISTINCT r.code) AS roles,
                   GROUP_CONCAT(DISTINCT perm.code) AS permissions
            FROM app_user u
            LEFT JOIN user_role ur ON ur.user_id = u.id AND ur.is_deleted = 0
            LEFT JOIN app_role r ON r.id = ur.role_id AND r.is_deleted = 0
            LEFT JOIN role_permission rp ON rp.role_id = r.id AND rp.is_deleted = 0
            LEFT JOIN permission perm ON perm.id = rp.permission_id AND perm.is_deleted = 0
            WHERE u.id = #{userId} AND u.is_deleted = 0 AND u.status <> 2
            GROUP BY u.id, u.phone, u.status
            """)
    PrincipalRow find(@Param("userId") Long userId);
}
