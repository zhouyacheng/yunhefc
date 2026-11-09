package com.yunhefc.registration.application;

import com.yunhefc.contracts.registration.MatchRosterPort;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import org.springframework.stereotype.Component;

@Component
public class RegistrationRosterAdapter implements MatchRosterPort {

    private final RegistrationMapper registrationMapper;

    public RegistrationRosterAdapter(RegistrationMapper registrationMapper) {
        this.registrationMapper = registrationMapper;
    }

    @Override
    public boolean isActiveFormalPlayer(Long matchId, Long userId) {
        return registrationMapper.countActiveFormalPlayer(matchId, userId) > 0;
    }
}
