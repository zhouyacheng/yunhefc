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
            SELECT COUNT(*)
            FROM registration r
            WHERE r.match_id = #{matchId} AND r.player_id = #{playerId}
              AND r.status IN (0, 1) AND r.is_deleted = 0
            """)
    int countActiveRegistration(@Param("matchId") Long matchId, @Param("playerId") Long playerId);

    @Select("""
            SELECT COUNT(*)
            FROM registration r
            WHERE r.match_id = #{matchId} AND r.type = #{type} AND r.status = #{status}
              AND r.is_deleted = 0
            """)
    int countByTypeAndStatus(@Param("matchId") Long matchId, @Param("type") int type,
                             @Param("status") int status);

    List<RegistrationQueueRow> selectQueueCounts(@Param("matchIds") Collection<Long> matchIds);

    // 管理员移除在持有比赛行锁后复核：当前读绕过事务快照与 MyBatis 本地缓存。
    @Select("""
            SELECT id, match_id, player_id, type, status, registered_at, cancelled_at,
                   cancelled_by, updated_at, is_deleted
            FROM registration
            WHERE id = #{id} AND is_deleted = 0
            FOR UPDATE
            """)
    Registration selectByIdForUpdate(@Param("id") Long id);

    // 注销编排使用当前读扫描：与报名事务的共享账号锁配合，注销后不残留有效报名。
    @Select("""
            SELECT id, match_id, player_id, type, status, registered_at, cancelled_at,
                   cancelled_by, updated_at, is_deleted
            FROM registration
            WHERE player_id = #{playerId} AND status IN (0, 1) AND is_deleted = 0
            FOR UPDATE
            """)
    List<Registration> selectActiveByPlayerIdForUpdate(@Param("playerId") Long playerId);

    // “我的报名”每场比赛只取最新一条，含已取消/移除历史；窗口查询见 RegistrationMapper.xml。
    List<Registration> selectLatestByPlayer(@Param("playerId") Long playerId);
}
