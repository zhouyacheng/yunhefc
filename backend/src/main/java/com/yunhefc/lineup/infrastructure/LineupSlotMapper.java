package com.yunhefc.lineup.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.lineup.domain.LineupSlot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LineupSlotMapper extends BaseMapper<LineupSlot> {

    @Select("""
            SELECT id, lineup_id, slot_key, slot_type, player_id, sort_order, is_deleted
            FROM lineup_slot
            WHERE lineup_id = #{lineupId} AND is_deleted = 0
            ORDER BY slot_type, sort_order, id
            """)
    List<LineupSlot> selectByLineupId(@Param("lineupId") Long lineupId);

    // 保存按阵容整体覆盖；唯一键不含 is_deleted，不能伪删除后重建。
    @Delete("DELETE FROM lineup_slot WHERE lineup_id = #{lineupId}")
    int deleteByLineupId(@Param("lineupId") Long lineupId);

    @Insert("""
            <script>
            INSERT INTO lineup_slot (lineup_id, slot_key, slot_type, player_id, sort_order)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.lineupId}, #{row.slotKey}, #{row.slotType}, #{row.playerId},
                 #{row.sortOrder})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<LineupSlot> rows);
}
