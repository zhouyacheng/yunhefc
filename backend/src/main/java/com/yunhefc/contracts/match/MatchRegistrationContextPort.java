package com.yunhefc.contracts.match;

import java.util.Optional;

/**
 * 报名域访问比赛名额上下文的端口；实现负责锁定 team_match 行。
 */
public interface MatchRegistrationContextPort {

    /**
     * 锁定比赛行并返回报名所需快照；比赛不存在或不属于该球队返回空。
     */
    Optional<MatchSnapshot> lockForRegistration(Long matchId, Long teamId);
}
