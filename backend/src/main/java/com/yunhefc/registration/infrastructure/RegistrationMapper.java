package com.yunhefc.registration.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationMapper {

    @Select("""
            SELECT COUNT(*)
            FROM registration r
            JOIN player p ON p.id = r.player_id
            WHERE r.match_id = #{matchId} AND p.user_id = #{userId}
              AND r.type = 1 AND r.status = 0
              AND r.is_deleted = 0 AND p.is_deleted = 0 AND p.type = 1
            """)
    int countActiveFormalPlayer(@Param("matchId") Long matchId, @Param("userId") Long userId);
}
