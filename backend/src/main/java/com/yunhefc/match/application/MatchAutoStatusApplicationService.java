package com.yunhefc.match.application;

import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchStatusMachine;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 比赛状态自动流转：按报名截止/锁定/开赛/结束时间推进，跳过手动覆盖、已延期、已取消与已结束。
 */
@Service
@RequiredArgsConstructor
public class MatchAutoStatusApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MatchAutoStatusApplicationService.class);

    private final TeamMatchMapper teamMatchMapper;
    private final Clock clock;

    /**
     * @return 本次实际推进的比赛数；重复执行返回 0。
     */
    public int advance() {
        Instant now = Instant.now(clock);
        int advanced = 0;
        for (TeamMatch match : teamMatchMapper.selectAutoAdvanceCandidates(now)) {
            MatchStatus target = MatchStatusMachine.autoTarget(
                    match.getStatus(), Boolean.TRUE.equals(match.getManualStatus()), now,
                    match.getSignupDeadline(), match.getCancelLock(),
                    match.getStartAt(), match.getEndAt());
            if (target == match.getStatus()) {
                continue;
            }
            advanced += teamMatchMapper.advanceStatus(match.getId(), match.getStatus().code(),
                    target.code(), match.getVersion(), now);
        }
        if (advanced > 0) {
            log.debug("Advanced match statuses automatically, count={}", advanced);
        }
        return advanced;
    }
}
