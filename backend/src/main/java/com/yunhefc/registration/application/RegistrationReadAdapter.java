package com.yunhefc.registration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.registration.RegistrationQueueCounts;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import com.yunhefc.registration.infrastructure.RegistrationQueueRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RegistrationReadAdapter implements RegistrationReadPort {

    private final RegistrationMapper registrationMapper;
    private final PlayerReadPort playerReadPort;

    @Override
    public Map<Long, RegistrationQueueCounts> queueCounts(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Map.of();
        }
        return registrationMapper.selectQueueCounts(matchIds).stream()
                .collect(Collectors.toMap(RegistrationQueueRow::getMatchId, RegistrationReadAdapter::toCounts));
    }

    @Override
    public Optional<RegistrationSnapshot> findActiveByUser(Long matchId, Long userId) {
        return playerReadPort.findByUserId(userId)
                .flatMap(player -> Optional.ofNullable(registrationMapper.selectOne(
                        new LambdaQueryWrapper<Registration>()
                                .eq(Registration::getMatchId, matchId)
                                .eq(Registration::getPlayerId, player.id())
                                .in(Registration::getStatus,
                                        RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
                                .last("LIMIT 1"))))
                .map(RegistrationReadAdapter::toSnapshot);
    }

    private static RegistrationQueueCounts toCounts(RegistrationQueueRow row) {
        return new RegistrationQueueCounts(
                row.getConfirmedCount(), row.getGuestCount(), row.getWaitlistCount());
    }

    private static RegistrationSnapshot toSnapshot(Registration registration) {
        return new RegistrationSnapshot(
                registration.getId(),
                registration.getPlayerId(),
                registration.getType().code(),
                registration.getType().label(),
                registration.getStatus().code(),
                registration.getStatus().label(),
                registration.getRegisteredAt(),
                registration.getCancelledAt(),
                registration.getStatus() == RegistrationStatus.CONFIRMED
                        || registration.getStatus() == RegistrationStatus.WAITLISTED);
    }
}
