package com.yunhefc.contracts.match;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MatchReadPort {

    Optional<MatchSnapshot> findById(Long matchId);

    List<MatchSnapshot> findByIds(Collection<Long> matchIds);

    /**
     * 已结束且实际结束时间不晚于 cutoff 的比赛；提前结束时用 status_updated_at，否则用 end_at。
     */
    List<MatchSnapshot> findFinishedBefore(Instant cutoff);
}
