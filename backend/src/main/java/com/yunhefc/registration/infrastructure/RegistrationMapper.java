package com.yunhefc.registration.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.registration.domain.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {

    @Select("""
            SELECT COUNT(*)
            FROM registration r
            WHERE r.match_id = #{matchId} AND r.player_id = #{playerId}
              AND r.type = 1 AND r.status = 0 AND r.is_deleted = 0
            """)
    int countActiveFormalRegistration(@Param("matchId") Long matchId, @Param("playerId") Long playerId);
}
