package com.yunhefc.stats.domain;

/**
 * 某球员在汇总窗口内的出场/进球/助攻。
 */
public record PlayerTally(int appearances, int goals, int assists) {

    public static final PlayerTally ZERO = new PlayerTally(0, 0, 0);
}
