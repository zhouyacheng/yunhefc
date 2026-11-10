package com.yunhefc.registration.application;

import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.dto.RegistrationDtos.RemovalResult;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RegistrationAdminApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAdminApplicationService.class);

    private final RegistrationMapper registrationMapper;
    private final MatchRegistrationContextPort matchRegistrationContextPort;
    private final RegistrationCancellationService registrationCancellationService;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    /**
     * REG-07：移除有效报名并留痕；移除 CONFIRMED 时同池最早候补递补，结果如实返回。
     * 先读报名取得比赛，再锁 team_match 行后复核，锁序与报名/取消一致。
     */
    @Transactional
    public RemovalResult remove(Long registrationId) {
        AuthPrincipal principal = CurrentUser.principal();
        Registration located = registrationMapper.selectById(registrationId);
        if (located == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报名不存在");
        }
        // 跨队报名在锁比赛时一并拒绝；锁后以当前读复核，避免事务快照返回锁前旧状态。
        MatchSnapshot match = matchRegistrationContextPort
                .lockForRegistration(located.getMatchId(), principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报名不存在"));

        Registration registration = registrationMapper.selectByIdForUpdate(registrationId);
        if (registration == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报名不存在");
        }
        requireRemovable(registration, match);

        Instant now = Instant.now(clock);
        Optional<Long> promotedPlayerId = registrationCancellationService
                .removeAndPromote(registration, principal.userId(), now);
        auditRecorder.recordCurrent("REGISTRATION_REMOVE", "REGISTRATION", registrationId,
                auditBefore(registration),
                auditAfter(registration, principal.userId(), now, promotedPlayerId.orElse(null)));
        log.info("registration removed, registrationId={}, matchId={}, playerId={}, promotedPlayerId={}",
                registrationId, registration.getMatchId(), registration.getPlayerId(),
                promotedPlayerId.orElse(null));
        return new RemovalResult(true, promotedPlayerId.orElse(null), promotedPlayerId.isPresent());
    }

    private void requireRemovable(Registration registration, MatchSnapshot match) {
        if (registration.getStatus() == RegistrationStatus.CANCELLED
                || registration.getStatus() == RegistrationStatus.REMOVED) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "报名已是终态");
        }
        if (!match.removalOpen()) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已结束或已取消");
        }
    }

    private static Map<String, Object> auditBefore(Registration registration) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("playerId", registration.getPlayerId());
        values.put("type", registration.getType().code());
        values.put("status", registration.getStatus().code());
        values.put("registeredAt", registration.getRegisteredAt());
        return values;
    }

    private static Map<String, Object> auditAfter(Registration registration, Long removedBy,
                                                  Instant now, Long promotedPlayerId) {
        Map<String, Object> values = auditBefore(registration);
        values.put("status", RegistrationStatus.REMOVED.code());
        values.put("cancelledAt", now);
        values.put("cancelledBy", removedBy);
        values.put("promotedPlayerId", promotedPlayerId);
        values.put("promoted", promotedPlayerId != null);
        return values;
    }
}
