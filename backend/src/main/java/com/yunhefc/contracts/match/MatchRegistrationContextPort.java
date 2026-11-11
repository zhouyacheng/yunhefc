package com.yunhefc.contracts.match;

import java.util.List;
import java.util.Optional;

/**
 * 报名域访问比赛名额上下文的端口；实现负责锁定 team_match 行。
 */
public interface MatchRegistrationContextPort {

    /**
     * 锁定比赛行并返回报名所需快照；比赛不存在或不属于该球队返回空。
     */
    Optional<MatchSnapshot> lockForRegistration(Long matchId, Long teamId);

    /**
     * 按主键升序锁定球队所有未开赛比赛行（报名中/报名截止/锁定/已延期）并返回当前快照。
     * 注销编排先锁比赛再扫描报名，避免与取消/递补在比赛与报名行上交叉等待；
     * 快照来自锁定读，不受事务一致性读视图影响。
     */
    List<MatchSnapshot> lockNotStartedMatches(Long teamId);
}
