package com.yunhefc.event.application;

import com.yunhefc.contracts.event.DerivedScoreSnapshot;
import com.yunhefc.contracts.event.EventScoreReadPort;
import com.yunhefc.event.domain.EventScore;
import com.yunhefc.event.domain.MatchEvent;
import com.yunhefc.event.infrastructure.MatchEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventScoreReadAdapter implements EventScoreReadPort {

    private final MatchEventMapper matchEventMapper;

    @Override
    public Optional<DerivedScoreSnapshot> findDerivedScore(Long matchId) {
        return Optional.ofNullable(findDerivedScores(List.of(matchId)).get(matchId));
    }

    @Override
    public Map<Long, DerivedScoreSnapshot> findDerivedScores(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Map.of();
        }
        return matchEventMapper.selectScoringByMatchIds(matchIds).stream()
                .collect(Collectors.groupingBy(MatchEvent::getMatchId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    EventScore score = EventScore.from(entry.getValue());
                    return new DerivedScoreSnapshot(score.scoreHome(), score.scoreAway());
                }));
    }
}
