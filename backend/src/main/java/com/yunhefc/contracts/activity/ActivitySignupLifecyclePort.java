package com.yunhefc.contracts.activity;

/**
 * 活动报名生命周期写端口；账号注销编排调用，与比赛报名注销保持同一语义。
 */
public interface ActivitySignupLifecyclePort {

    /**
     * 取消球员在未结束活动（报名中/报名截止）中的有效报名；已结束/已取消活动保留历史。
     */
    void cancelActiveForDisabledPlayer(Long teamId, Long playerId, Long cancelledBy);
}
