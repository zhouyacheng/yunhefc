package com.yunhefc.match.application;

import com.yunhefc.match.domain.TeamMatch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 比赛审计快照：管理员操作与系统自动流转共用同一字段集合与顺序。
 */
final class MatchAuditSnapshot {

    private MatchAuditSnapshot() {
    }

    static Map<String, Object> of(TeamMatch match) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", match.getTitle());
        values.put("type", match.getType().code());
        values.put("format", match.getFormat());
        values.put("startAt", match.getStartAt());
        values.put("endAt", match.getEndAt());
        values.put("signupDeadline", match.getSignupDeadline());
        values.put("cancelLock", match.getCancelLock());
        values.put("venue", match.getVenue());
        values.put("competition", match.getCompetition());
        values.put("season", match.getSeason());
        values.put("round", match.getRound());
        values.put("homeTeamName", match.getHomeTeamName());
        values.put("awayTeamName", match.getAwayTeamName());
        values.put("ourSide", match.getOurSide() == null ? null : match.getOurSide().code());
        values.put("status", match.getStatus().code());
        values.put("manualStatus", match.getManualStatus());
        values.put("capacity", match.getCapacity());
        values.put("guestCapacity", match.getGuestCapacity());
        values.put("waitlistCapacity", match.getWaitlistCapacity());
        values.put("includeGuests", match.getIncludeGuests());
        values.put("scoreHome", match.getScoreHome());
        values.put("scoreAway", match.getScoreAway());
        values.put("seasonMatchNo", match.getSeasonMatchNo());
        values.put("totalMatchNo", match.getTotalMatchNo());
        values.put("statusUpdatedAt", match.getStatusUpdatedAt());
        values.put("statusUpdatedBy", match.getStatusUpdatedBy());
        return values;
    }
}
