package com.yunhefc.attendance.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.attendance.domain.Attendance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Mapper
public interface AttendanceMapper extends BaseMapper<Attendance> {

    @Select("""
            SELECT player_id
            FROM attendance
            WHERE match_id = #{matchId} AND is_deleted = 0
            """)
    List<Long> selectPlayerIdsByMatch(@Param("matchId") Long matchId);

    @Select("""
            <script>
            SELECT id, match_id, player_id, status, source, recorded_at
            FROM attendance
            WHERE is_deleted = 0 AND match_id IN
            <foreach collection="matchIds" item="matchId" open="(" separator="," close=")">
                #{matchId}
            </foreach>
            </script>
            """)
    List<Attendance> selectByMatchIds(@Param("matchIds") Collection<Long> matchIds);

    /**
     * 批量补记缺勤；ON DUPLICATE KEY 保证并发/重复执行不覆盖已有记录。
     */
    int insertAbsences(@Param("matchId") Long matchId,
                       @Param("playerIds") Collection<Long> playerIds,
                       @Param("status") int status,
                       @Param("source") int source,
                       @Param("recordedAt") Instant recordedAt);
}
