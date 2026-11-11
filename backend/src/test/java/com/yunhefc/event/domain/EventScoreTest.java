package com.yunhefc.event.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventScoreTest {

    @Test
    void goalCountsForOwnTeamAndOwnGoalCountsForOpponent() {
        EventScore score = EventScore.from(List.of(
                event(EventTeam.HOME, EventType.GOAL),
                event(EventTeam.HOME, EventType.OWN_GOAL),
                event(EventTeam.AWAY, EventType.GOAL),
                event(EventTeam.AWAY, EventType.OWN_GOAL),
                event(EventTeam.HOME, EventType.YELLOW_CARD)));

        assertThat(score.scoreHome()).isEqualTo(2);
        assertThat(score.scoreAway()).isEqualTo(2);
    }

    @Test
    void eventListWithoutGoalsDerivesNilNil() {
        EventScore score = EventScore.from(List.of(
                event(EventTeam.HOME, EventType.RED_CARD),
                event(EventTeam.AWAY, EventType.SUBSTITUTION)));

        assertThat(score.scoreHome()).isZero();
        assertThat(score.scoreAway()).isZero();
    }

    private static MatchEvent event(EventTeam team, EventType type) {
        MatchEvent event = new MatchEvent();
        event.setTeam(team);
        event.setType(type);
        return event;
    }
}
