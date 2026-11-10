package com.yunhefc.registration.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.registration.domain.Registration;
import com.yunhefc.registration.domain.RegistrationStatus;
import com.yunhefc.registration.infrastructure.RegistrationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * 取消/移除报名与同池递补的共享实现；普通取消、管理员移除与 P-05 注销编排复用。
 * 调用方必须已持有对应比赛的 team_match 行锁，递补因此按比赛串行。
 */
@Service
@RequiredArgsConstructor
public class RegistrationCancellationService {

    private final RegistrationMapper registrationMapper;

    /**
     * 取消有效报名；取消 CONFIRMED 时同池最早 WAITLISTED 递补。
     *
     * @return 被递补球员 ID；未触发递补返回空
     */
    public Optional<Long> cancelAndPromote(Registration registration, Long cancelledBy, Instant now) {
        return terminateAndPromote(registration, cancelledBy, now, RegistrationStatus.CANCELLED);
    }

    /**
     * 管理员移除有效报名；移除 CONFIRMED 时同池最早 WAITLISTED 递补。
     *
     * @return 被递补球员 ID；未触发递补返回空
     */
    public Optional<Long> removeAndPromote(Registration registration, Long removedBy, Instant now) {
        return terminateAndPromote(registration, removedBy, now, RegistrationStatus.REMOVED);
    }

    private Optional<Long> terminateAndPromote(Registration registration, Long terminatedBy,
                                               Instant now, RegistrationStatus terminalStatus) {
        int terminated = registrationMapper.update(null, new LambdaUpdateWrapper<Registration>()
                .eq(Registration::getId, registration.getId())
                .in(Registration::getStatus,
                        RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED)
                .set(Registration::getStatus, terminalStatus)
                .set(Registration::getCancelledAt, now)
                .set(Registration::getCancelledBy, terminatedBy));
        if (terminated == 0 || registration.getStatus() != RegistrationStatus.CONFIRMED) {
            return Optional.empty();
        }
        return promoteEarliestWaitlisted(registration);
    }

    /**
     * 同池顺序：registered_at、id 升序；一次取消最多递补一人。
     */
    private Optional<Long> promoteEarliestWaitlisted(Registration cancelled) {
        Registration candidate = registrationMapper.selectOne(new LambdaQueryWrapper<Registration>()
                .eq(Registration::getMatchId, cancelled.getMatchId())
                .eq(Registration::getType, cancelled.getType())
                .eq(Registration::getStatus, RegistrationStatus.WAITLISTED)
                .orderByAsc(Registration::getRegisteredAt)
                .orderByAsc(Registration::getId)
                .last("LIMIT 1 FOR UPDATE"));
        if (candidate == null) {
            return Optional.empty();
        }
        int promoted = registrationMapper.update(null, new LambdaUpdateWrapper<Registration>()
                .eq(Registration::getId, candidate.getId())
                .eq(Registration::getStatus, RegistrationStatus.WAITLISTED)
                .set(Registration::getStatus, RegistrationStatus.CONFIRMED));
        return promoted > 0 ? Optional.of(candidate.getPlayerId()) : Optional.empty();
    }
}
