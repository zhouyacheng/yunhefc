package com.yunhefc.contracts.team;

/**
 * 球队只读端口；单队 MVP 由 auth 实现。
 */
public interface TeamReadPort {

    /**
     * 当前球队 ID。
     */
    Long currentTeamId();
}
