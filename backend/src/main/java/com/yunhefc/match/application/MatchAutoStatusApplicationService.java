package com.yunhefc.match.application;

import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchStatusMachine;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * 比赛状态自动流转：按报名截止/锁定/开赛/结束时间推进，跳过手动覆盖、已延期、已取消与已结束。
 * 每次实际推进与审计 Outbox 在同一事务内提交。
 */
@Service
@RequiredArgsConstructor
public class MatchAutoStatusApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MatchAutoStatusApplicationService.class);
    private static final String AUDIT_ACTION = "MATCH_AUTO_STATUS_CHANGE";
    private static final String AUDIT_ACTOR_NAME = "系统";
    private static final String AUDIT_TARGET_TYPE = "MATCH";

    private final TeamMatchMapper teamMatchMapper;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @return 本次实际推进的比赛数；重复执行返回 0。
     */
    public int advance() {
        Instant now = Instant.now(clock);
        int advanced = 0;
        for (TeamMatch candidate : teamMatchMapper.selectAutoAdvanceCandidates(now)) {
            try {
                Integer changed = transactionTemplate.execute(
                        status -> advanceOne(candidate.getId(), candidate.getTeamId(), now));
                advanced += changed == null ? 0 : changed;
            } catch (RuntimeException ex) {
                // 单场失败不影响同一轮其他比赛的推进；下一分钟按主键幂等重试。
                log.warn("Auto advance failed, matchId={}", candidate.getId(), ex);
            }
        }
        if (advanced > 0) {
            log.debug("Advanced match statuses automatically, count={}", advanced);
        }
        return advanced;
    }

    private int advanceOne(Long matchId, Long teamId, Instant now) {
        // 候选在事务外读出，锁行后按最新数据重新判定，避免覆盖并发的手动修改。
        TeamMatch match = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (match == null) {
            return 0;
        }
        MatchStatus target = MatchStatusMachine.autoTarget(
                match.getStatus(), Boolean.TRUE.equals(match.getManualStatus()), now,
                match.getSignupDeadline(), match.getCancelLock(),
                match.getStartAt(), match.getEndAt());
        if (target == match.getStatus()) {
            return 0;
        }

        Map<String, Object> before = MatchAuditSnapshot.of(match);
        int updated = teamMatchMapper.advanceStatus(matchId, match.getStatus().code(),
                target.code(), match.getVersion(), now);
        if (updated == 0) {
            return 0;
        }
        // 审计 after 取落库后的行，避免快照与 advanceStatus 的 SET 列漂移。
        TeamMatch saved = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (saved == null) {
            throw new IllegalStateException("自动流转后比赛不存在: " + matchId);
        }
        auditRecorder.record(teamId, null, AUDIT_ACTOR_NAME, AUDIT_ACTION,
                AUDIT_TARGET_TYPE, matchId, before, MatchAuditSnapshot.of(saved));
        return 1;
    }
}
