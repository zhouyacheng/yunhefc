package com.yunhefc.contracts.registration;

public interface MatchRosterPort {

    boolean isActiveFormalPlayer(Long matchId, Long userId);
}
