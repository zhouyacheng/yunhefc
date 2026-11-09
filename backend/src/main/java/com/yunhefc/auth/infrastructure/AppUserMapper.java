package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * updateById 默认忽略 null 字段，解绑微信必须显式清空 openid。
     */
    @Update("UPDATE app_user SET openid = NULL WHERE id = #{id} AND is_deleted = 0")
    int clearOpenid(@Param("id") Long id);
}
