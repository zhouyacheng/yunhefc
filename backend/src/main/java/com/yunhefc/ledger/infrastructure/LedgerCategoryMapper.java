package com.yunhefc.ledger.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.ledger.domain.LedgerCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LedgerCategoryMapper extends BaseMapper<LedgerCategory> {

    /**
     * 分类与未删除流水数；方向过滤与 LED-08/09 一致，包含通用分类。
     */
    List<LedgerCategoryRow> selectWithUsage(@Param("teamId") Long teamId,
                                            @Param("direction") Integer direction,
                                            @Param("includeInactive") boolean includeInactive);

    /**
     * 按球队读取分类；分类不存在或属于他队返回 null。
     */
    @Select("""
            SELECT id, team_id, name, direction, sort_no, is_active,
                   created_at, updated_at, is_deleted
            FROM ledger_category
            WHERE id = #{id} AND team_id = #{teamId} AND is_deleted = 0
            """)
    LedgerCategory selectByIdAndTeam(@Param("id") Long id, @Param("teamId") Long teamId);
}
