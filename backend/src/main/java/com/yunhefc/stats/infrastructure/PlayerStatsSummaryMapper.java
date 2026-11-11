package com.yunhefc.stats.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.stats.domain.PlayerStatsSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface PlayerStatsSummaryMapper extends BaseMapper<PlayerStatsSummary> {

    @Select("""
            SELECT player_id
            FROM player_stats_summary
            WHERE team_id = #{teamId} AND season = #{season} AND is_deleted = 0
            """)
    List<Long> selectPlayerIds(@Param("teamId") Long teamId, @Param("season") String season);

    /**
     * 按 (team_id, player_id, season) 批量覆盖；重复执行结果一致。
     */
    @Insert("""
            <script>
            INSERT INTO player_stats_summary
                (team_id, player_id, season, appearances, goals, assists, created_at, updated_at, is_deleted)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.teamId}, #{row.playerId}, #{row.season}, #{row.appearances}, #{row.goals},
                 #{row.assists}, #{now}, #{now}, 0)
            </foreach>
            ON DUPLICATE KEY UPDATE
                appearances = VALUES(appearances),
                goals = VALUES(goals),
                assists = VALUES(assists),
                is_deleted = 0,
                updated_at = VALUES(updated_at)
            </script>
            """)
    int upsertBatch(@Param("rows") List<PlayerStatsSummary> rows, @Param("now") Instant now);
}
