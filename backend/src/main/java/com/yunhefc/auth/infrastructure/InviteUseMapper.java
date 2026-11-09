package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.InviteUse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InviteUseMapper extends BaseMapper<InviteUse> {

    @Insert("""
            INSERT INTO invite_use (invite_id, user_id)
            VALUES (#{inviteId}, #{userId})
            ON DUPLICATE KEY UPDATE is_deleted = 0
            """)
    int upsert(@Param("inviteId") Long inviteId, @Param("userId") Long userId);
}
