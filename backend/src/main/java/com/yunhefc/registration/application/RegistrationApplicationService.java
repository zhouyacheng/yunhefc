package com.yunhefc.registration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationPolicy;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.domain.RegistrationType;
import com.yunhefc.registration.dto.RegistrationDtos.CancellationResult;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistrationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationApplicationService.class);

    private final RegistrationMapper registrationMapper;
    private final MatchRegistrationContextPort matchRegistrationContextPort;
    private final PlayerReadPort playerReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final RegistrationCancellationService registrationCancellationService;
    private final OutboxService outboxService;
    private final Clock clock;

    /**
     * 报名与名额判断在同一事务内：先共享锁账号、再锁定比赛行，后续报名写入按比赛串行化。
     * 锁序与注销编排一致（账号→比赛），避免并发注销后残留有效报名。
     */
    @Transactional
    public RegistrationView register(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        requireActiveAccount(principal);

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
        if (status == RegistrationStatus.CONFIRMED) {
            outboxService.append(match.teamId(), "MATCH", matchId,
                    EventTypes.REGISTRATION_CONFIRMED,
                    RegistrationNotificationPayload.of(match, principal.userId()));
        }
        log.info("registration created, registrationId={}, matchId={}, playerId={}, status={}",
                registration.getId(), matchId, player.id(), status.code());
        return toView(registration);
    }

    /**
     * REG-02：锁定前可自行取消；取消 CONFIRMED 时同池最早候补递补，结果如实返回。
     */
    @Transactional
    public CancellationResult cancelMyRegistration(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        // 与报名相同，先共享锁账号再锁比赛；注销编排的排他锁保证两者不会交错。
        requireActiveAccount(principal);

        MatchSnapshot match = matchRegistrationContextPort
                .lockForRegistration(matchId, principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));

        Instant now = Instant.now(clock);
        requireCancellationOpen(match, now);

        PlayerSnapshot player = playerReadPort.findByUserId(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "无有效报名"));
        Registration registration = registrationMapper.selectOne(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getMatchId, matchId)
                .eq(Registration::getPlayerId, player.id())
                .in(Registration::getStatus,
                        RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
                .last("LIMIT 1"));
        if (registration == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "无有效报名");
        }

        Optional<Long> promotedPlayerId = registrationCancellationService.cancelAndPromote(
                registration, principal.userId(), now, match);
        log.info("registration cancelled, registrationId={}, matchId={}, playerId={}, promotedPlayerId={}",
                registration.getId(), matchId, player.id(), promotedPlayerId.orElse(null));
        return new CancellationResult(true, promotedPlayerId.orElse(null), promotedPlayerId.isPresent());
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

    /**
     * 共享锁账号并校验 ACTIVE，锁持有至事务结束。注销编排持有排他锁时阻塞，
     * 解锁后读到 DISABLED，保证注销后报名/取消不会写入有效报名。
     */
    private void requireActiveAccount(AuthPrincipal principal) {
        boolean active = userAccountReadPort.lockActiveForRegistration(principal.userId())
                .orElse(false);
        if (!active) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号不可用");
        }
    }

    /**
     * 报名截止只停新增，锁定后才停取消（锁定后仅管理员可移除）；已取消/已延期的比赛不能取消。
     */
    private void requireCancellationOpen(MatchSnapshot match, Instant now) {
        if (!match.cancellationOpen()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛状态不允许取消报名");
        }
        if (!now.isBefore(match.cancelLock())) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "已过取消截止");
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
