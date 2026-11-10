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

    // 报名截止是最早的时间边界；到达后由应用层按截止/锁定/开赛/结束推导目标状态。
    @Select("""
            SELECT id, status, manual_status, signup_deadline, cancel_lock, start_at, end_at, version
            FROM team_match
            WHERE is_deleted = 0 AND manual_status = 0
              AND status IN (0, 1, 2, 3)
              AND signup_deadline <= #{now}
            """)
    List<TeamMatch> selectAutoAdvanceCandidates(@Param("now") Instant now);

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
