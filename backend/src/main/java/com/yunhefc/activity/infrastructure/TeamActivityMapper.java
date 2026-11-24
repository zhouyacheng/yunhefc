package com.yunhefc.activity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.activity.domain.TeamActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamActivityMapper extends BaseMapper<TeamActivity> {

    /**
     * 当前读并锁定活动行：管理员编辑/状态流转互相串行，且 0 行更新后的重读不受
     * REPEATABLE READ 快照影响。
     */
    @Select("""
            SELECT id, team_id, title, type, start_at, end_at, signup_deadline, capacity,
                   cost_per_person, location, location_image_key, description, status,
                   cancel_reason, version, created_by, created_at, updated_at, is_deleted
            FROM team_activity
            WHERE id = #{id} AND team_id = #{teamId} AND is_deleted = 0
            FOR UPDATE
            """)
    TeamActivity selectByIdForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);

    /**
     * 共享锁读取活动：本人取消/管理员移除与状态流转（FOR UPDATE）串行，
     * 防止活动进入终态后仍落取消/移除写。
     */
    @Select("""
            SELECT id, team_id, title, type, start_at, end_at, signup_deadline, capacity,
                   cost_per_person, location, location_image_key, description, status,
                   cancel_reason, version, created_by, created_at, updated_at, is_deleted
            FROM team_activity
            WHERE id = #{id} AND team_id = #{teamId} AND is_deleted = 0
            FOR SHARE
            """)
    TeamActivity selectByIdForShare(@Param("id") Long id, @Param("teamId") Long teamId);
}
