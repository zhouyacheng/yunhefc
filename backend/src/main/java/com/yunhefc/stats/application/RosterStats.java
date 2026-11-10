package com.yunhefc.stats.application;

import com.yunhefc.contracts.player.PlayerPublicSnapshot;

/**
 * 球队页公开名单与本赛季汇总的组合行。
 */
public record RosterStats(PlayerPublicSnapshot player, PlayerStatsRow stats) {
}
