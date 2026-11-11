package com.yunhefc.contracts.match;

import java.time.Instant;
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
    List<MatchSnapshot> findFinishedBefore(Instant cutoff);

    /**
     * 球队指定赛季的未取消比赛，供统计汇总全量扫描；赛季为空按开赛年份。
     */
    List<MatchSnapshot> findSeasonMatches(Long teamId, String season);
}
