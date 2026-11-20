package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * updateById 默认忽略 null 字段，解绑微信必须显式清空 openid。
     */
    @Update("UPDATE app_user SET openid = NULL WHERE id = #{id} AND is_deleted = 0")
    int clearOpenid(@Param("id") Long id);

    @Update("UPDATE app_user SET last_login_at = #{lastLoginAt} WHERE id = #{id} AND is_deleted = 0")
    int updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    @Update("UPDATE app_user SET password_hash = #{passwordHash} WHERE id = #{id} AND is_deleted = 0")
    int updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Update("UPDATE app_user SET phone = #{phone} WHERE id = #{id} AND is_deleted = 0")
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);

    @Update("UPDATE app_user SET openid = #{openid} WHERE id = #{id} AND is_deleted = 0")
    int updateOpenid(@Param("id") Long id, @Param("openid") String openid);

    @Update("UPDATE app_user SET mp_openid = NULL WHERE id = #{id} AND is_deleted = 0")
    int clearMpOpenid(@Param("id") Long id);

    @Update("UPDATE app_user SET mp_openid = #{mpOpenid} WHERE id = #{id} AND is_deleted = 0")
    int updateMpOpenid(@Param("id") Long id, @Param("mpOpenid") String mpOpenid);

    @Update("UPDATE app_user SET status = #{status} WHERE id = #{id} AND is_deleted = 0")
    int updateStatus(@Param("id") Long id, @Param("status") int status);

    /**
     * 报名事务的共享锁：注销编排持有排他锁时阻塞，解锁后读到 DISABLED，避免注销后残留报名。
     */
    @Select("SELECT status FROM app_user WHERE id = #{id} AND is_deleted = 0 FOR SHARE")
    Integer selectStatusByIdForShare(@Param("id") Long id);
}
