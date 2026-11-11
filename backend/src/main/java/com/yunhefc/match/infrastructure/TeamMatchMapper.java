package com.yunhefc.match.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.match.domain.TeamMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface TeamMatchMapper extends BaseMapper<TeamMatch> {

    // 场次编号必须包含伪删除记录：唯一键不随 is_deleted 释放，跳过它们会撞唯一约束。
    @Select("SELECT COALESCE(MAX(total_match_no), 0) FROM team_match WHERE team_id = #{teamId}")
    Long maxTotalMatchNo(@Param("teamId") Long teamId);

    @Select("""
            SELECT COALESCE(MAX(season_match_no), 0)
            FROM team_match
            WHERE team_id = #{teamId} AND season = #{season}
            """)
    Long maxSeasonMatchNo(@Param("teamId") Long teamId, @Param("season") String season);

    // 编辑前锁定比赛行：与报名域的名额行锁串行化，避免计数与容量校验之间插队。
    @Select("""
            SELECT * FROM team_match
            WHERE id = #{id} AND team_id = #{teamId} AND is_deleted = 0
            FOR UPDATE
            """)
    TeamMatch selectByIdForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);

    // 注销编排先锁定全部未开赛比赛行，再扫描报名：与报名/取消/递补共用比赛行锁，且不持有报名行等待比赛。
    @Select("""
            SELECT * FROM team_match
            WHERE team_id = #{teamId} AND is_deleted = 0 AND status IN (0, 1, 2, 5)
            ORDER BY id
            FOR UPDATE
            """)
    List<TeamMatch> lockNotStartedMatches(@Param("teamId") Long teamId);

    // 只取按当前状态已到达下一时间边界的行，避免每分钟对无推进的比赛加锁。
    @Select("""
            SELECT id, team_id, status, manual_status, signup_deadline, cancel_lock, start_at, end_at, version
            FROM team_match
            WHERE is_deleted = 0 AND manual_status = 0
              AND signup_deadline <= #{now}
              AND (status = 0
                   OR (status = 1 AND cancel_lock <= #{now})
                   OR (status = 2 AND start_at <= #{now})
                   OR (status = 3 AND end_at <= #{now}))
            """)
    List<TeamMatch> selectAutoAdvanceCandidates(@Param("now") Instant now);

    // 考勤缺勤候选：已结束且实际结束时间已到 cutoff；提前结束用 status_updated_at，否则用 end_at。
    @Select("""
            SELECT id, team_id, title, type, our_side, start_at, end_at, signup_deadline,
                   cancel_lock, capacity, guest_capacity, waitlist_capacity, venue, status,
                   home_team_name, away_team_name, status_updated_at, season
            FROM team_match
            WHERE status = #{status} AND is_deleted = 0
              AND (CASE WHEN status_updated_at IS NOT NULL AND status_updated_at < end_at
                        THEN status_updated_at ELSE end_at END) <= #{cutoff}
            ORDER BY id
            """)
    List<TeamMatch> selectFinishedBefore(@Param("status") int status,
                                         @Param("cutoff") Instant cutoff);

    // 统计汇总候选：未取消比赛；赛季为空的行也要取出，由应用层按开赛年份裁决。
    @Select("""
            SELECT id, team_id, title, type, our_side, start_at, end_at, signup_deadline,
                   cancel_lock, capacity, guest_capacity, waitlist_capacity, venue, status,
                   home_team_name, away_team_name, status_updated_at, season
            FROM team_match
            WHERE team_id = #{teamId} AND is_deleted = 0 AND status <> #{cancelled}
              AND (season = #{season} OR season IS NULL OR season = '')
            ORDER BY id
            """)
    List<TeamMatch> selectSeasonMatches(@Param("teamId") Long teamId,
                                        @Param("season") String season,
                                        @Param("cancelled") int cancelled);

    // 按主键 + 当前状态 + 版本条件更新：任务重复执行或与他人并发修改时不产生副作用。
    @Update("""
            UPDATE team_match
            SET status = #{target}, status_updated_at = #{now}, status_updated_by = NULL,
                version = version + 1
            WHERE id = #{id} AND is_deleted = 0 AND manual_status = 0
              AND status = #{current} AND version = #{version}
            """)
    int advanceStatus(@Param("id") Long id, @Param("current") int current,
                      @Param("target") int target, @Param("version") int version,
                      @Param("now") Instant now);
}
