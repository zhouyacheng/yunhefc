package com.yunhefc.event.domain;

import java.util.Collection;

/**
 * 比分推导：进球记事件所属队，乌龙记对方（需求 §6.6 规则 11）。
 */
public record EventScore(int scoreHome, int scoreAway) {

    public static EventScore from(Collection<MatchEvent> events) {
        int home = 0;
        int away = 0;
        for (MatchEvent event : events) {
            if (event.getType() == EventType.GOAL) {
                if (event.getTeam() == EventTeam.HOME) {
                    home++;
                } else {
                    away++;
                }
            } else if (event.getType() == EventType.OWN_GOAL) {
                if (event.getTeam() == EventTeam.HOME) {
                    away++;
                } else {
                    home++;
                }
            }
        }
        return new EventScore(home, away);
    }
}
