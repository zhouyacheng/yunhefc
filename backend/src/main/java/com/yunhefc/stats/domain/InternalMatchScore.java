package com.yunhefc.stats.domain;

/**
 * 队内赛最终比分（主队/客队）；两项均已知才计入胜率分母。
 */
public record InternalMatchScore(Integer scoreHome, Integer scoreAway) {

    public boolean known() {
        return scoreHome != null && scoreAway != null;
    }
}
