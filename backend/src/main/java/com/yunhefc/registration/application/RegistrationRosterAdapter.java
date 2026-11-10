package com.yunhefc.registration.application;

import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.registration.MatchRosterPort;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegistrationRosterAdapter implements MatchRosterPort {

    private final RegistrationMapper registrationMapper;
    private final PlayerReadPort playerReadPort;

    @Override
    public boolean isActiveFormalPlayer(Long matchId, Long userId) {
        PlayerSnapshot player = playerReadPort.findByUserId(userId).orElse(null);
        return player != null && player.type() == 1
                && registrationMapper.countActiveFormalRegistration(matchId, player.id()) > 0;
    }
}
