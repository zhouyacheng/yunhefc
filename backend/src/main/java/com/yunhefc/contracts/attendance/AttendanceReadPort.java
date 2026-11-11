package com.yunhefc.contracts.attendance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceReadPort {

    /**
     * 球员在某场比赛的考勤记录；无记录返回空。
     */
    Optional<AttendanceSnapshot> findByMatchAndPlayer(Long matchId, Long playerId);

    /**
     * 批量读取多场比赛的全部考勤，供统计汇总全量重算。
     */
    List<AttendanceSnapshot> findByMatchIds(Collection<Long> matchIds);
}
