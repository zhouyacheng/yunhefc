package com.yunhefc.contracts.match;

/**
 * 比赛状态编码；contracts 不能依赖比赛域枚举，集中在此避免快照各自硬编码。
 */
final class MatchStatusCodes {

    static final int SIGNUP_OPEN = 0;
    static final int SIGNUP_CLOSED = 1;
    static final int LOCKED = 2;
    static final int IN_PROGRESS = 3;
    static final int FINISHED = 4;
    static final int POSTPONED = 5;
    static final int CANCELLED = 6;

    private MatchStatusCodes() {
    }
}
