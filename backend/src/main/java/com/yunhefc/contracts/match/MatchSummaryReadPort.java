package com.yunhefc.contracts.match;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 比赛摘要只读端口；供跨域读模型组合“我的报名”等列表使用，读取范围限定球队。
 */
public interface MatchSummaryReadPort {

    /**
     * 比赛不存在或不属于该球队时返回空。
     */
    Optional<MatchSummarySnapshot> findSummary(Long matchId, Long teamId);

    /**
     * 批量读取球队比赛摘要；不存在或不属于该球队的比赛不返回键。
     */
    Map<Long, MatchSummarySnapshot> findSummaries(Collection<Long> matchIds, Long teamId);
}
