package com.yunhefc.event.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.event.domain.MatchEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface MatchEventMapper extends BaseMapper<MatchEvent> {

    /**
     * 时间线：半场升序 → 分钟升序 → 同分钟进球优先 → id 升序。
     * 软删除需同时满足 is_deleted=0 与 deleted_at 为空。
     */
    @Select("""
            SELECT id, match_id, team, type, player_id, assist_player_id, in_player_id,
                   out_player_id, minute, half, penalty, created_by, created_at, updated_by,
                   updated_at, deleted_at, deleted_by, version, is_deleted
            FROM match_event
            WHERE match_id = #{matchId} AND is_deleted = 0 AND deleted_at IS NULL
            ORDER BY half ASC, minute ASC,
                     CASE WHEN type IN (0, 1) THEN 0 ELSE 1 END ASC,
                     id ASC
            """)
    List<MatchEvent> selectTimeline(@Param("matchId") Long matchId);

    @Select("""
            SELECT id, match_id, team, type, player_id, assist_player_id, in_player_id,
                   out_player_id, minute, half, penalty, created_by, created_at, updated_by,
                   updated_at, deleted_at, deleted_by, version, is_deleted
            FROM match_event
            WHERE match_id = #{matchId} AND is_deleted = 0 AND deleted_at IS NULL
            """)
    List<MatchEvent> selectActiveByMatch(@Param("matchId") Long matchId);

    // 伪删除保留原记录：同时写 is_deleted、deleted_at、deleted_by，并按 version 乐观锁收敛并发。
    @Update("""
            UPDATE match_event
            SET is_deleted = 1, deleted_at = #{now}, deleted_by = #{userId},
                updated_by = #{userId}, updated_at = #{now}, version = version + 1
            WHERE id = #{id} AND version = #{version} AND is_deleted = 0 AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") Long id, @Param("version") int version,
                   @Param("userId") Long userId, @Param("now") Instant now);
}
