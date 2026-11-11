package com.yunhefc.contracts.registration;

import java.util.Collection;
import java.util.List;
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

    /**
     * 球员在每场比赛中的最新一条报名（含取消/移除历史），按报名时间、ID 降序。
     */
    List<RegistrationSnapshot> findLatestByPlayer(Long playerId);

    /**
     * 比赛报名名单分页；type/status 为空时不过滤，按报名时间、ID 升序。
     */
    RegistrationRosterPage findRoster(Long matchId, Integer type, Integer status, long page, long pageSize);

    /**
     * 比赛全部有效报名（CONFIRMED/WAITLISTED）对应账号，按报名时间、ID 升序去重。
     */
    List<Long> findActiveUserIds(Long teamId, Long matchId);

    /**
     * 比赛 CONFIRMED 报名的 playerId，按报名时间、ID 升序。
     */
    List<Long> findConfirmedPlayerIds(Long matchId);
}
