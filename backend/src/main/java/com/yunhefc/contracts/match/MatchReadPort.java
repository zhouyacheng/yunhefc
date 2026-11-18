package com.yunhefc.contracts.match;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchReadPort {

    Optional<MatchSnapshot> findById(Long matchId);

    /**
     * 写路径读取比赛并锁定行，与取消/延期等状态变更串行化。
     */
    Optional<MatchSnapshot> findByIdForUpdate(Long matchId, Long teamId);

    List<MatchSnapshot> findByIds(Collection<Long> matchIds);

    /**
     * 已结束且实际结束时间不晚于 cutoff 的比赛；提前结束时用 status_updated_at，否则用 end_at。
     */
    List<MatchSnapshot> findFinishedBefore(LocalDateTime cutoff);

    /**
     * 球队全部未取消比赛，供统计汇总按有效赛季分组重算。
     */
    List<MatchSnapshot> findRollupMatches(Long teamId);

    /**
     * 分组写路径锁比赛行，串行化同场生成/调整/保存/清除。
     */
    Optional<MatchSnapshot> lockForGrouping(Long matchId, Long teamId);
}
