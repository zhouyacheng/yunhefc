package com.yunhefc.event.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunhefc.event.domain.EventHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventHistoryMapper extends BaseMapper<EventHistory> {
}
