package com.yunhefc.match.application;

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

    static MatchSummarySnapshot toSummary(TeamMatch match) {
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
                match.getScoreHome(),
                match.getScoreAway(),
                resultText(match),
                match.getStatus().code(),
                match.getStatus().label(),
                match.getTotalMatchNo(),
                match.getSeasonMatchNo());
    }

    /**
     * 仅已结束比赛给出结果：对外按我方视角胜/负/平，队内/训练给出比分文本；无比分时为 null。
     */
    static String resultText(TeamMatch match) {
        if (match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }
        Integer home = match.getScoreHome();
        Integer away = match.getScoreAway();
        if (home == null || away == null) {
            return null;
        }
        OurSide side = match.getOurSide();
        if (side == null) {
            return home + ":" + away;
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
