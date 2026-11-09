package com.yunhefc.auth.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RolePermissionMapper {

    @Select("""
            SELECT p.code
            FROM permission p
            JOIN role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId} AND rp.is_deleted = 0 AND p.is_deleted = 0
            """)
    List<String> findPermissionCodes(@Param("roleId") Long roleId);

    @Update("UPDATE role_permission SET is_deleted = 1 WHERE role_id = #{roleId} AND is_deleted = 0")
    int removeAll(@Param("roleId") Long roleId);

    @Insert("""
            INSERT INTO role_permission (role_id, permission_id)
            VALUES (#{roleId}, #{permissionId})
            ON DUPLICATE KEY UPDATE is_deleted = 0
            """)
    int upsert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM app_user u
            JOIN user_role ur ON ur.user_id = u.id AND ur.is_deleted = 0
            JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.is_deleted = 0
            JOIN permission p ON p.id = rp.permission_id AND p.is_deleted = 0
            WHERE u.status = 1 AND u.is_deleted = 0 AND p.code = 'role:manage:any'
            """)
    int countActiveUsersWithRoleManage();
}
