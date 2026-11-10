package com.yunhefc.stats.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.stats.domain.PlayerStatsSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlayerStatsSummaryMapper extends BaseMapper<PlayerStatsSummary> {
}
