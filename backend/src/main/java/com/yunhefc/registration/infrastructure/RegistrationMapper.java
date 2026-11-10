package com.yunhefc.registration.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.registration.domain.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {

    @Select("""
            SELECT COUNT(*)
            FROM registration r
            WHERE r.match_id = #{matchId} AND r.player_id = #{playerId}
              AND r.type = 1 AND r.status = 0 AND r.is_deleted = 0
            """)
    int countActiveFormalRegistration(@Param("matchId") Long matchId, @Param("playerId") Long playerId);

    @Select("""
            <script>
            SELECT match_id AS matchId,
                   SUM(CASE WHEN type = 1 AND status = 0 THEN 1 ELSE 0 END) AS confirmedCount,
                   SUM(CASE WHEN type = 0 AND status = 0 THEN 1 ELSE 0 END) AS guestCount,
                   SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) AS waitlistCount
            FROM registration
            WHERE is_deleted = 0
              AND match_id IN
              <foreach collection="matchIds" item="matchId" open="(" separator="," close=")">
                #{matchId}
              </foreach>
            GROUP BY match_id
            </script>
            """)
    List<RegistrationQueueRow> selectQueueCounts(@Param("matchIds") Collection<Long> matchIds);
}
