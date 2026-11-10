package com.yunhefc.auth.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserRoleMapper {

    @Select("""
            SELECT r.code
            FROM app_role r
            JOIN user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND ur.is_deleted = 0 AND r.is_deleted = 0
            """)
    List<String> findRoleCodes(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.code
            FROM permission p
            JOIN role_permission rp ON rp.permission_id = p.id
            JOIN user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId}
              AND ur.is_deleted = 0 AND rp.is_deleted = 0 AND p.is_deleted = 0
            """)
    List<String> findPermissionCodes(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT ur.user_id, r.code
            FROM user_role ur
            JOIN app_role r ON r.id = ur.role_id
            WHERE ur.user_id IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
              AND ur.is_deleted = 0 AND r.is_deleted = 0
            </script>
            """)
    List<UserRoleRow> findRoleCodesByUserIds(@Param("userIds") List<Long> userIds);

    @Select("SELECT role_id FROM user_role WHERE user_id = #{userId} AND is_deleted = 0")
    List<Long> findRoleIds(@Param("userId") Long userId);

    @Insert("""
            INSERT INTO user_role (user_id, role_id)
            VALUES (#{userId}, #{roleId})
            ON DUPLICATE KEY UPDATE is_deleted = 0
            """)
    int upsert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Insert("""
            <script>
            INSERT INTO user_role (user_id, role_id)
            VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            ON DUPLICATE KEY UPDATE is_deleted = 0
            </script>
            """)
    int upsertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    @Update("UPDATE user_role SET is_deleted = 1 WHERE user_id = #{userId} AND is_deleted = 0")
    int removeAll(@Param("userId") Long userId);

    @Update("""
            UPDATE user_role SET is_deleted = 1
            WHERE user_id = #{userId} AND role_id = #{roleId} AND is_deleted = 0
            """)
    int remove(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT COUNT(*) FROM user_role WHERE role_id = #{roleId} AND is_deleted = 0")
    int countUsers(@Param("roleId") Long roleId);

    @Select("""
            SELECT ur.user_id
            FROM user_role ur
            JOIN app_user u ON u.id = ur.user_id
            WHERE ur.role_id = #{roleId} AND ur.is_deleted = 0
              AND u.is_deleted = 0 AND u.status = 1
            """)
    List<Long> findActiveUserIdsByRole(@Param("roleId") Long roleId);

    @Select("""
            SELECT COUNT(*)
            FROM user_role ur
            JOIN app_user u ON u.id = ur.user_id
            WHERE ur.role_id = #{roleId} AND ur.is_deleted = 0
              AND u.is_deleted = 0 AND u.status = 1
            """)
    int countActiveUsers(@Param("roleId") Long roleId);
}
