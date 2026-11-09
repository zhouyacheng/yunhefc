package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.InviteLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InviteLinkMapper extends BaseMapper<InviteLink> {

    @Select("SELECT * FROM invite_link WHERE token_hash = #{tokenHash} AND is_deleted = 0 LIMIT 1")
    InviteLink findByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE invite_link
            SET used_count = used_count + 1
            WHERE id = #{id} AND is_deleted = 0
              AND (max_uses IS NULL OR used_count < max_uses)
            """)
    int incrementUsedCount(@Param("id") Long id);

    @Update("""
            UPDATE invite_link
            SET status = 2
            WHERE status = 0 AND expires_at < #{now} AND is_deleted = 0
            """)
    int expireOverdue(@Param("now") LocalDateTime now);
}
