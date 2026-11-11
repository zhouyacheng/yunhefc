package com.yunhefc.event.application;

import com.yunhefc.contracts.event.EventStatsReadPort;
import com.yunhefc.contracts.event.EventStatsSnapshot;
import com.yunhefc.event.domain.MatchEvent;
import com.yunhefc.event.infrastructure.MatchEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EventStatsReadAdapter implements EventStatsReadPort {

    private final MatchEventMapper matchEventMapper;

    @Override
    public List<EventStatsSnapshot> findByMatchIds(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return List.of();
        }
        return matchEventMapper.selectStatsEvents(matchIds).stream()
                .map(EventStatsReadAdapter::toSnapshot)
                .toList();
    }

    private static EventStatsSnapshot toSnapshot(MatchEvent event) {
        return new EventStatsSnapshot(
                event.getMatchId(),
                event.getType().code(),
                event.getPlayerId(),
                event.getAssistPlayerId());
    }
}
