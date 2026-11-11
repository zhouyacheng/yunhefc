package com.yunhefc.stats.application;

/**
 * 一次统计汇总的结果摘要。
 */
public record RollupResult(Long teamId, String season, int matchCount, int playerCount) {
}
