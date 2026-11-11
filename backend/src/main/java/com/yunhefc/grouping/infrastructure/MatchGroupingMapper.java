package com.yunhefc.grouping.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.grouping.domain.MatchGrouping;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.Instant;

@Mapper
public interface MatchGroupingMapper extends BaseMapper<MatchGrouping> {

    // 分组表唯一键不含 is_deleted，覆盖写需要能看到并复活伪删除行。
    @Select("""
            SELECT id, match_id, manual_weight, auto_weight, roster_fingerprint, generated_at,
                   saved_at, created_by, created_at, updated_at, is_deleted
            FROM match_grouping
            WHERE match_id = #{matchId}
            """)
    MatchGrouping selectByMatchIncludingDeleted(@Param("matchId") Long matchId);

    @Update("""
            UPDATE match_grouping
            SET manual_weight = #{manualWeight}, auto_weight = #{autoWeight},
                roster_fingerprint = #{rosterFingerprint}, generated_at = #{generatedAt},
                saved_at = NULL, created_by = #{createdBy}, is_deleted = 0
            WHERE id = #{id}
            """)
    int overwrite(@Param("id") Long id,
                  @Param("manualWeight") BigDecimal manualWeight,
                  @Param("autoWeight") BigDecimal autoWeight,
                  @Param("rosterFingerprint") String rosterFingerprint,
                  @Param("generatedAt") Instant generatedAt,
                  @Param("createdBy") Long createdBy);

    @Update("""
            UPDATE match_grouping
            SET saved_at = NULL
            WHERE id = #{id}
            """)
    int resetSavedAt(@Param("id") Long id);

    @Update("""
            UPDATE match_grouping
            SET saved_at = #{savedAt}
            WHERE id = #{id}
            """)
    int markSaved(@Param("id") Long id, @Param("savedAt") Instant savedAt);

    @Delete("DELETE FROM match_grouping WHERE id = #{id}")
    int deletePhysical(@Param("id") Long id);
}
