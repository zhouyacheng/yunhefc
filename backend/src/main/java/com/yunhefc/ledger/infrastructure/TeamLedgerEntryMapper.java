package com.yunhefc.ledger.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TeamLedgerEntryMapper extends BaseMapper<LedgerEntryEntity> {

    LedgerSummaryRow selectSummary(@Param("teamId") Long teamId,
                                   @Param("incomeCode") int incomeCode,
                                   @Param("expenseCode") int expenseCode,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to,
                                   @Param("type") Integer type,
                                   @Param("categoryId") Long categoryId,
                                   @Param("playerId") Long playerId,
                                   @Param("activityId") Long activityId);

    /**
     * 当前读并锁定流水行：版本冲突时读取最新版本返回给调用方。
     */
    @Select("""
            SELECT id, team_id, type, category_id, amount, description, occurred_at,
                   player_id, activity_id, version, created_by, created_at, updated_at, is_deleted
            FROM team_ledger_entry
            WHERE id = #{id} AND team_id = #{teamId} AND is_deleted = 0
            FOR UPDATE
            """)
    LedgerEntryEntity selectByIdForUpdate(@Param("id") Long id, @Param("teamId") Long teamId);

    @Update("""
            UPDATE team_ledger_entry
            SET is_deleted = 1, version = version + 1
            WHERE id = #{id} AND team_id = #{teamId} AND version = #{version} AND is_deleted = 0
            """)
    int logicalDelete(@Param("id") Long id, @Param("teamId") Long teamId,
                      @Param("version") Integer version);
}
