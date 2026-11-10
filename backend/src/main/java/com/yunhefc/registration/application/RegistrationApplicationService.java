package com.yunhefc.registration.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationPolicy;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.domain.RegistrationType;
import com.yunhefc.registration.dto.RegistrationDtos.RegistrationView;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RegistrationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationApplicationService.class);

    private final RegistrationMapper registrationMapper;
    private final MatchRegistrationContextPort matchRegistrationContextPort;
    private final PlayerReadPort playerReadPort;
    private final Clock clock;

    /**
     * 报名与名额判断在同一事务内：先锁定比赛行，后续报名写入按比赛串行化。
     */
    @Transactional
    public RegistrationView register(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        MatchSnapshot match = matchRegistrationContextPort
                .lockForRegistration(matchId, principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));

        Instant now = Instant.now(clock);
        requireSignupOpen(match, now);

        PlayerSnapshot player = playerReadPort.findByUserId(principal.userId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.MATCH_STATE_CONFLICT, "球员档案不存在"));

        if (registrationMapper.countActiveRegistration(matchId, player.id()) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_REGISTRATION);
        }

        RegistrationType type = player.type() == PlayerType.PLAYER.code()
                ? RegistrationType.PLAYER : RegistrationType.GUEST;
        int confirmedInPool = registrationMapper.countByTypeAndStatus(
                matchId, type.code(), RegistrationStatus.CONFIRMED.code());
        int waitlistCount = registrationMapper.countByTypeAndStatus(
                matchId, RegistrationType.PLAYER.code(), RegistrationStatus.WAITLISTED.code());
        RegistrationStatus status = RegistrationPolicy.decide(type, confirmedInPool, waitlistCount,
                match.capacity(), match.guestCapacity(), match.waitlistCapacity());

        Registration registration = new Registration();
        registration.setMatchId(matchId);
        registration.setPlayerId(player.id());
        registration.setType(type);
        registration.setStatus(status);
        registration.setRegisteredAt(now);
        try {
            registrationMapper.insert(registration);
        } catch (DuplicateKeyException ex) {
            log.debug("registration unique key conflict, matchId={}, playerId={}",
                    matchId, player.id(), ex);
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_REGISTRATION);
        }
        log.info("registration created, registrationId={}, matchId={}, playerId={}, status={}",
                registration.getId(), matchId, player.id(), status.code());
        return toView(registration);
    }

    private void requireSignupOpen(MatchSnapshot match, Instant now) {
        if (!match.signupOpen()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛当前不可报名");
        }
        if (!now.isBefore(match.signupDeadline())) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "已过报名截止");
        }
        if (!now.isBefore(match.startAt())) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已开始");
        }
    }

    private static RegistrationView toView(Registration registration) {
        return new RegistrationView(
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
