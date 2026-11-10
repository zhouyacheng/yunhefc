package com.yunhefc.contracts.registration;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface RegistrationReadPort {

    /**
     * 批量读取比赛报名队列计数；无报名记录的比赛不返回键。
     */
    Map<Long, RegistrationQueueCounts> queueCounts(Collection<Long> matchIds);

    /**
     * 本人当前有效报名；未报名返回空。
     */
    Optional<RegistrationSnapshot> findActiveByUser(Long matchId, Long userId);
}
