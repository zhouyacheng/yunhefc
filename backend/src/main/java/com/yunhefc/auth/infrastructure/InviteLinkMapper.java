package com.yunhefc.auth.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.auth.domain.InviteLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface InviteLinkMapper extends BaseMapper<InviteLink> {

    @Select("""
            SELECT id, team_id, type, token_hash, match_id, inviter_user_id, expires_at,
                   max_uses, used_count, status, revoked_at, created_at, updated_at, is_deleted
            FROM invite_link
            WHERE token_hash = #{tokenHash} AND is_deleted = 0
            LIMIT 1
            """)
    InviteLink findByTokenHash(@Param("tokenHash") String tokenHash);

    @Select("""
            SELECT id, team_id, type, token_hash, match_id, inviter_user_id, expires_at,
                   max_uses, used_count, status, revoked_at, created_at, updated_at, is_deleted
            FROM invite_link
            WHERE token_hash = #{tokenHash} AND is_deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    InviteLink findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Select("""
            SELECT id, team_id, type, token_hash, match_id, inviter_user_id, expires_at,
                   max_uses, used_count, status, revoked_at, created_at, updated_at, is_deleted
            FROM invite_link
            WHERE id = #{id} AND is_deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    InviteLink findByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE invite_link
            SET used_count = used_count + 1
            WHERE id = #{id} AND is_deleted = 0
              AND status = 0 AND expires_at > #{now}
              AND (max_uses IS NULL OR used_count < max_uses)
            """)
    int incrementUsedCount(@Param("id") Long id, @Param("now") Instant now);

    @Update("""
            UPDATE invite_link
            SET status = 1, revoked_at = #{now}
            WHERE id = #{id} AND is_deleted = 0 AND status <> 1
            """)
    int revoke(@Param("id") Long id, @Param("now") Instant now);

    @Update("""
            UPDATE invite_link
            SET status = 2
            WHERE status = 0 AND expires_at < #{now} AND is_deleted = 0
            """)
    int expireOverdue(@Param("now") Instant now);
}
