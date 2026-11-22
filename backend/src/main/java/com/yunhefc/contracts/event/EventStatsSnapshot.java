package com.yunhefc.contracts.event;

/**
 * 统计汇总读取的事件快照；只保留统计需要的字段。
 */
public record EventStatsSnapshot(
        Long matchId,
        Integer type,
        Long playerId,
        Long assistPlayerId) {

    private static final int GOAL = 0;
    private static final int OWN_GOAL = 1;

    /**
     * 个人进球口径：只有 GOAL 计入；乌龙（OWN_GOAL）记对方比分，不计个人进球。
     */
    public boolean personalGoal() {
        return type != null && type == GOAL && playerId != null;
    }

    /**
     * 乌龙球口径：按 OWN_GOAL 的球员单列统计，不抵扣进球。
     */
    public boolean ownGoal() {
        return type != null && type == OWN_GOAL && playerId != null;
    }
}
