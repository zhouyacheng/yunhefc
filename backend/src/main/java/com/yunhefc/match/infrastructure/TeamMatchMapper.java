package com.yunhefc.match.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.match.domain.TeamMatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
