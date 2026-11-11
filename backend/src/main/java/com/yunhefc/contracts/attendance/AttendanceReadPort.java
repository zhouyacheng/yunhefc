package com.yunhefc.contracts.attendance;

import java.util.Optional;

public interface AttendanceReadPort {

    /**
     * 球员在某场比赛的考勤记录；无记录返回空。
     */
    Optional<AttendanceSnapshot> findByMatchAndPlayer(Long matchId, Long playerId);
}
