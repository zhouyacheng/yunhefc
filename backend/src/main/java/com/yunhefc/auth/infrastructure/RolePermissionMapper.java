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
            <script>
            INSERT INTO role_permission (role_id, permission_id)
            VALUES
            <foreach collection="permissionIds" item="permissionId" separator=",">
                (#{roleId}, #{permissionId})
            </foreach>
            ON DUPLICATE KEY UPDATE is_deleted = 0
            </script>
            """)
    int upsertBatch(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM app_user u
            JOIN user_role ur ON ur.user_id = u.id AND ur.is_deleted = 0
            JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.is_deleted = 0
            JOIN permission p ON p.id = rp.permission_id AND p.is_deleted = 0
            WHERE u.status = 1 AND u.is_deleted = 0 AND p.code = 'role:manage:any'
            """)
    int countActiveUsersWithRoleManage();

    /**
     * 模拟剔除指定用户后，仍拥有 role:manage:any 的 ACTIVE 用户数；用于注销前的最后管理员保护。
     */
    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM app_user u
            JOIN user_role ur ON ur.user_id = u.id AND ur.is_deleted = 0
            JOIN role_permission rp ON rp.role_id = ur.role_id AND rp.is_deleted = 0
            JOIN permission p ON p.id = rp.permission_id AND p.is_deleted = 0
            WHERE u.status = 1 AND u.is_deleted = 0 AND p.code = 'role:manage:any'
              AND u.id <> #{userId}
            """)
    int countActiveUsersWithRoleManageExcluding(@Param("userId") Long userId);
}
