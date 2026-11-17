package com.yunhefc.match.application;

import com.yunhefc.contracts.event.DerivedScoreSnapshot;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.OurSide;
import com.yunhefc.match.domain.TeamMatch;

/**
 * 比赛摘要字段的唯一映射入口；公开列表与跨域只读端口共用，避免展示规则分叉。
 */
final class MatchSummaryMapping {

    private MatchSummaryMapping() {
    }

    /**
     * 有效比分：存在计分事件时取事件推导值，否则取 team_match 兜底比分。
     */
    record ResolvedScore(Integer scoreHome, Integer scoreAway) {
    }

    static ResolvedScore resolveScore(TeamMatch match, DerivedScoreSnapshot derived) {
        if (derived != null) {
            return new ResolvedScore(derived.scoreHome(), derived.scoreAway());
        }
        return new ResolvedScore(match.getScoreHome(), match.getScoreAway());
    }

    static MatchSummarySnapshot toSummary(TeamMatch match, DerivedScoreSnapshot derived) {
        ResolvedScore score = resolveScore(match, derived);
        return new MatchSummarySnapshot(
                match.getId(),
                match.getTeamId(),
                match.getTitle(),
                match.getType().code(),
                match.getType().label(),
                match.getStartAt(),
                match.getEndAt(),
                match.getVenue(),
                match.getRound(),
                match.getSeason(),
                match.getHomeTeamName(),
                match.getAwayTeamName(),
                sideCode(match),
                sideLabel(match),
                score.scoreHome(),
                score.scoreAway(),
                resultText(match, score),
                match.getStatus().code(),
                match.getStatus().label(),
                match.getTotalMatchNo(),
                match.getSeasonMatchNo());
    }

    /**
     * 仅已结束的对外比赛给出结果：按我方视角胜/负/平；队内/训练与无比分时为 null。
     */
    private static String resultText(TeamMatch match, ResolvedScore score) {
        if (match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }
        Integer home = score.scoreHome();
        Integer away = score.scoreAway();
        if (home == null || away == null) {
            return null;
        }
        OurSide side = match.getOurSide();
        if (side == null) {
            return null;
        }
        int ours = side == OurSide.HOME ? home : away;
        int theirs = side == OurSide.HOME ? away : home;
        if (ours > theirs) {
            return "胜";
        }
        return ours < theirs ? "负" : "平";
    }

    private static Integer sideCode(TeamMatch match) {
        return match.getOurSide() == null ? null : match.getOurSide().code();
    }

    private static String sideLabel(TeamMatch match) {
        return match.getOurSide() == null ? null : match.getOurSide().label();
    }
}
