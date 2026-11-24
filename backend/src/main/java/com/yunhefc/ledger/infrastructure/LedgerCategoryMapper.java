package com.yunhefc.ledger.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.ledger.domain.LedgerCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LedgerCategoryMapper extends BaseMapper<LedgerCategory> {

    /**
     * 分类与未删除流水数；方向过滤与 LED-08/09 一致，包含通用分类。
     */
    List<LedgerCategoryRow> selectWithUsage(@Param("teamId") Long teamId,
                                            @Param("direction") Integer direction,
                                            @Param("includeInactive") boolean includeInactive);
}
