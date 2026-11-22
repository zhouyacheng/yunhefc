package com.yunhefc.stats.domain;

/**
 * 某球员在汇总窗口内的出场/进球/助攻/乌龙/队内赛出场与胜场。
 */
public record PlayerTally(int appearances, int goals, int assists,
                          int ownGoals, int internalAppearances, int internalWins) {

    public static final PlayerTally ZERO = new PlayerTally(0, 0, 0, 0, 0, 0);
}
