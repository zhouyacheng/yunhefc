package com.yunhefc.registration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.registration.RegistrationLifecyclePort;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.domain.RegistrationType;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationLifecycleApplicationService implements RegistrationLifecyclePort {

    private final RegistrationMapper registrationMapper;
    private final MatchReadPort matchReadPort;
    private final Clock clock;

    @Override
    @Transactional
    public void cancelActiveForDisabledPlayer(Long teamId, Long playerId, Long cancelledBy) {
        List<Registration> active = registrationMapper.selectList(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getPlayerId, playerId)
                .in(Registration::getStatus,
                        RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED));
        if (active.isEmpty()) {
            return;
        }

        Set<Long> matchIds = active.stream().map(Registration::getMatchId).collect(Collectors.toSet());
        Map<Long, MatchSnapshot> notStarted = matchReadPort.findByIds(matchIds).stream()
                .filter(match -> Objects.equals(match.teamId(), teamId) && match.notStarted())
                .collect(Collectors.toMap(MatchSnapshot::id, Function.identity()));

        Instant now = Instant.now(clock);
        for (Registration registration : active) {
            if (!notStarted.containsKey(registration.getMatchId())) {
                continue;
            }
            boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;
            int cancelled = registrationMapper.update(null, new LambdaUpdateWrapper<Registration>()
                    .eq(Registration::getId, registration.getId())
                    .eq(Registration::getStatus, registration.getStatus())
                    .set(Registration::getStatus, RegistrationStatus.CANCELLED)
                    .set(Registration::getCancelledAt, now)
                    .set(Registration::getCancelledBy, cancelledBy));
            if (cancelled > 0 && wasConfirmed) {
                promote(registration.getMatchId(), registration.getType());
            }
        }
    }

    /**
     * 同池最早正式候补；行锁避免并发递补同一候补。
     */
    private void promote(Long matchId, RegistrationType type) {
        Registration candidate = registrationMapper.selectOne(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getMatchId, matchId)
                .eq(Registration::getType, type)
                .eq(Registration::getStatus, RegistrationStatus.WAITLISTED)
                .orderByAsc(Registration::getRegisteredAt)
                .orderByAsc(Registration::getId)
                .last("LIMIT 1 FOR UPDATE"));
        if (candidate == null) {
            return;
        }
        registrationMapper.update(null, new LambdaUpdateWrapper<Registration>()
                .eq(Registration::getId, candidate.getId())
                .eq(Registration::getStatus, RegistrationStatus.WAITLISTED)
                .set(Registration::getStatus, RegistrationStatus.CONFIRMED));
    }
}
