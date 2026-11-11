package com.yunhefc.grouping.domain;

/**
 * 分组评分使用的赛季汇总；无汇总行按全 0 处理。
 */
public record GroupingPlayerStats(int appearances, int goals, int assists) {

    public static final GroupingPlayerStats EMPTY = new GroupingPlayerStats(0, 0, 0);
}
