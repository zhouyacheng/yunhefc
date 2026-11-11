package com.yunhefc.event.application;

import com.yunhefc.contracts.event.DerivedScoreSnapshot;
import com.yunhefc.contracts.event.EventScoreReadPort;
import com.yunhefc.event.domain.EventScore;
import com.yunhefc.event.domain.MatchEvent;
import com.yunhefc.event.infrastructure.MatchEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EventScoreReadAdapter implements EventScoreReadPort {

    private final MatchEventMapper matchEventMapper;

    @Override
    public Optional<DerivedScoreSnapshot> findDerivedScore(Long matchId) {
        List<MatchEvent> events = matchEventMapper.selectActiveByMatch(matchId);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        EventScore score = EventScore.from(events);
        return Optional.of(new DerivedScoreSnapshot(score.scoreHome(), score.scoreAway()));
    }
}
