package com.yunhefc.registration.application;

import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.registration.RegistrationLifecyclePort;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RegistrationLifecycleApplicationService implements RegistrationLifecyclePort {

    private final RegistrationMapper registrationMapper;
    private final MatchRegistrationContextPort matchRegistrationContextPort;
    private final RegistrationCancellationService registrationCancellationService;
    private final Clock clock;

    /**
     * P-05 注销编排：账号行已被排他锁定；先按主键升序锁定全部未开赛比赛行，再以当前读扫描
     * 有效报名并取消。报名/取消都先取账号共享锁，同一账号不会与注销交错；不同账号只在
     * 比赛行上单向等待，且注销不在持有报名行时等待比赛，避免与递补相互死锁。
     */
    @Override
    @Transactional
    public void cancelActiveForDisabledPlayer(Long teamId, Long playerId, Long cancelledBy) {
        Map<Long, MatchSnapshot> cancellable = matchRegistrationContextPort
                .lockNotStartedMatches(teamId).stream()
                .collect(Collectors.toMap(MatchSnapshot::id, Function.identity()));
        List<Registration> active = registrationMapper.selectActiveByPlayerIdForUpdate(playerId);
        if (active.isEmpty()) {
            return;
        }

        Instant now = Instant.now(clock);
        for (Registration registration : active) {
            MatchSnapshot match = cancellable.get(registration.getMatchId());
            if (match != null) {
                registrationCancellationService.cancelAndPromote(
                        registration, cancelledBy, now, match);
            }
        }
    }
}
