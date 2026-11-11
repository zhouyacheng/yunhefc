package com.yunhefc.contracts.event;

import java.util.Collection;
import java.util.List;

/**
 * 统计汇总只读端口：批量读取比赛未删除事件。
 */
public interface EventStatsReadPort {

    List<EventStatsSnapshot> findByMatchIds(Collection<Long> matchIds);
}
