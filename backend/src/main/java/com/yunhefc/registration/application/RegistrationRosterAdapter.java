package com.yunhefc.registration.application;

import com.yunhefc.contracts.registration.MatchRosterPort;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationRosterAdapter implements MatchRosterPort {

    private final RegistrationMapper registrationMapper;

    @Override
    public boolean isActiveFormalPlayer(Long matchId, Long userId) {
        return registrationMapper.countActiveFormalPlayer(matchId, userId) > 0;
    }
}
