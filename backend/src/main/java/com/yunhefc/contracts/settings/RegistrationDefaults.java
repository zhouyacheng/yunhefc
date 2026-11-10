package com.yunhefc.contracts.settings;

/**
 * 新建比赛时快照的报名容量默认值。
 */
public record RegistrationDefaults(int maxPlayers, int maxGuests, int maxWaitlist) {
}
