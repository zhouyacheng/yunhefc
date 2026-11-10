package com.yunhefc.contracts.registration;

/**
 * 报名生命周期写端口；账号注销编排（P-05）调用，完整 M04 接口复用同一实现。
 */
public interface RegistrationLifecyclePort {

    /**
     * 取消球员在未开赛比赛中的有效报名；CONFIRMED 取消后由同池最早正式候补递补。
     */
    void cancelActiveForDisabledPlayer(Long teamId, Long playerId, Long cancelledBy);
}
