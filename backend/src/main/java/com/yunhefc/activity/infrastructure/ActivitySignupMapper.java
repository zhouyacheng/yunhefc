package com.yunhefc.activity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.activity.domain.ActivitySignup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ActivitySignupMapper extends BaseMapper<ActivitySignup> {

    @Select("""
            SELECT COUNT(*)
            FROM activity_signup
            WHERE activity_id = #{activityId} AND status = 0 AND is_deleted = 0
            """)
    int countActive(@Param("activityId") Long activityId);

    List<ActivitySignupCountRow> selectActiveCounts(@Param("activityIds") Collection<Long> activityIds);

    /**
     * 注销编排：只取消未结束活动的有效报名，已结束/已取消活动保留历史行。
     */
    @Update("""
            UPDATE activity_signup s
            JOIN team_activity a ON a.id = s.activity_id
            SET s.status = 1, s.cancelled_at = NOW(3), s.cancelled_by = #{cancelledBy}
            WHERE s.player_id = #{playerId} AND s.status = 0 AND s.is_deleted = 0
              AND a.team_id = #{teamId} AND a.status IN (0, 1) AND a.is_deleted = 0
            """)
    int cancelActiveForDisabledPlayer(@Param("teamId") Long teamId,
                                      @Param("playerId") Long playerId,
                                      @Param("cancelledBy") Long cancelledBy);
}
