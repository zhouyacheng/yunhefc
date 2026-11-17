package com.yunhefc.event.domain;

import java.util.Collection;

/**
 * 比分推导：进球记事件所属队，乌龙记对方（需求 §6.6 规则 11）。
 */
public record EventScore(int scoreHome, int scoreAway) {

    /**
     * 是否存在计分事件（进球/乌龙）；黄牌/红牌/换人不参与比分推导。
     */
    public static boolean hasScoringEvent(Collection<MatchEvent> events) {
        return events.stream()
                .anyMatch(event -> event.getType() == EventType.GOAL
                        || event.getType() == EventType.OWN_GOAL);
    }

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
