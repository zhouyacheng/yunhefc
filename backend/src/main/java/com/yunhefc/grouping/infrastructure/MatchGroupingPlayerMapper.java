package com.yunhefc.grouping.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.grouping.domain.MatchGroupingPlayer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MatchGroupingPlayerMapper extends BaseMapper<MatchGroupingPlayer> {

    @Select("""
            SELECT id, grouping_id, player_id, team, manual_score, auto_score, final_score,
                   created_at, is_deleted
            FROM match_grouping_player
            WHERE grouping_id = #{groupingId}
            ORDER BY player_id
            """)
    List<MatchGroupingPlayer> selectByGroupingId(@Param("groupingId") Long groupingId);

    @Delete("DELETE FROM match_grouping_player WHERE grouping_id = #{groupingId}")
    int deleteByGroupingId(@Param("groupingId") Long groupingId);

    // 调整分组只改归属，保留成员行 id、分数快照与 created_at（04 §5.20）。
    @Update("""
            UPDATE match_grouping_player
            SET team = #{team}
            WHERE grouping_id = #{groupingId} AND player_id = #{playerId}
            """)
    int updateTeam(@Param("groupingId") Long groupingId,
                   @Param("playerId") Long playerId,
                   @Param("team") int team);

    @Insert("""
            <script>
            INSERT INTO match_grouping_player
                (grouping_id, player_id, team, manual_score, auto_score, final_score)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.groupingId}, #{row.playerId}, #{row.team}, #{row.manualScore},
                 #{row.autoScore}, #{row.finalScore})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<MatchGroupingPlayer> rows);
}
