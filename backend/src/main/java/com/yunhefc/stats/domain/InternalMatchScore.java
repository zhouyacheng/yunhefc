package com.yunhefc.stats.domain;

/**
 * 队内赛最终比分（主队/客队）；两项均已知才计入胜率分母。
 */
public record InternalMatchScore(Integer scoreHome, Integer scoreAway) {

    private static final int TEAM_HOME = 0;
    private static final int TEAM_AWAY = 1;

    public boolean known() {
        return scoreHome != null && scoreAway != null;
    }

    /**
     * 分组 0=A 队/主队、1=B 队/客队；平局计入分母但不算胜，未知队伍或比分不算胜。
     */
    public boolean teamWon(Integer team) {
        if (!known() || team == null) {
            return false;
        }
        if (team == TEAM_HOME) {
            return scoreHome > scoreAway;
        }
        if (team == TEAM_AWAY) {
            return scoreAway > scoreHome;
        }
        return false;
    }
}
