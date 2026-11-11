package com.yunhefc.lineup.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.lineup.domain.Lineup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LineupMapper extends BaseMapper<Lineup> {

    // 阵容从不删除；显式读取并过滤 is_deleted，与唯一键 (match_id, side) 对齐。
    @Select("""
            SELECT id, match_id, side, formation_template_id, version, updated_by,
                   created_at, updated_at, is_deleted
            FROM lineup
            WHERE match_id = #{matchId} AND side = #{side} AND is_deleted = 0
            """)
    Lineup selectByMatchAndSide(@Param("matchId") Long matchId, @Param("side") int side);
}
