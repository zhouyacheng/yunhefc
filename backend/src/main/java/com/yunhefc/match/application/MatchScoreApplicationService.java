package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.event.EventScoreReadPort;
import com.yunhefc.contracts.match.MatchScoreChange;
import com.yunhefc.contracts.match.MatchScoreSnapshot;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * EVT-05 兜底比分写入：锁比赛行后校验状态、版本与计分事件，与赛程编辑共用同一 version。
 */
@Service
@RequiredArgsConstructor
public class MatchScoreApplicationService {

    private final TeamMatchMapper teamMatchMapper;
    private final EventScoreReadPort eventScoreReadPort;

    @Transactional
    public MatchScoreChange updateScore(Long matchId, Long teamId, Integer scoreHome,
                                        Integer scoreAway, int version) {
        TeamMatch match = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        requireActive(match.getStatus());
        if (version != match.getVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "比赛已被他人修改，请刷新后重试",
                    Map.of("currentVersion", match.getVersion()));
        }
        // 与事件写共用比赛行锁，锁内判定计分事件，避免“检查后并发插入进球/乌龙”的竞态。
        if (eventScoreReadPort.findDerivedScore(matchId).isPresent()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "该比赛已有进球或乌龙事件，最终比分按事件推导；请调整或删除计分事件后再修改兜底比分");
        }

        MatchScoreSnapshot before = snapshot(match);
        int updated = teamMatchMapper.update(null, new LambdaUpdateWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId)
                .eq(TeamMatch::getVersion, version)
                .notIn(TeamMatch::getStatus, MatchStatus.CANCELLED, MatchStatus.POSTPONED)
                .set(TeamMatch::getScoreHome, scoreHome)
                .set(TeamMatch::getScoreAway, scoreAway)
                .set(TeamMatch::getVersion, version + 1));
        if (updated == 0) {
            TeamMatch current = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
            }
            requireActive(current.getStatus());
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "比赛已被他人修改，请刷新后重试",
                    Map.of("currentVersion", current.getVersion()));
        }
        return new MatchScoreChange(before, new MatchScoreSnapshot(scoreHome, scoreAway, version + 1));
    }

    private static void requireActive(MatchStatus status) {
        if (status == MatchStatus.CANCELLED || status == MatchStatus.POSTPONED) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "比赛已取消或已延期，不能修改比分");
        }
    }

    private static MatchScoreSnapshot snapshot(TeamMatch match) {
        return new MatchScoreSnapshot(match.getScoreHome(), match.getScoreAway(), match.getVersion());
    }
}
