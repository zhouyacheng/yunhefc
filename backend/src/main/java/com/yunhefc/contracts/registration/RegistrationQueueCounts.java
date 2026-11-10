package com.yunhefc.contracts.registration;

/**
 * 比赛报名队列计数：正式已确认、外挂已确认与正式候补。
 */
public record RegistrationQueueCounts(int confirmedCount, int guestCount, int waitlistCount) {

    public static final RegistrationQueueCounts EMPTY = new RegistrationQueueCounts(0, 0, 0);
}
