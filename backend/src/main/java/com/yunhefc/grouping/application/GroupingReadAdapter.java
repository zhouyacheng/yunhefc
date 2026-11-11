package com.yunhefc.grouping.application;

import com.yunhefc.contracts.grouping.GroupingReadPort;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import com.yunhefc.grouping.domain.MatchGrouping;
import com.yunhefc.grouping.domain.MatchGroupingPlayer;
import com.yunhefc.grouping.infrastructure.MatchGroupingMapper;
import com.yunhefc.grouping.infrastructure.MatchGroupingPlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GroupingReadAdapter implements GroupingReadPort {

    private final MatchGroupingMapper groupingMapper;
    private final MatchGroupingPlayerMapper groupingPlayerMapper;

    @Override
    public Optional<SavedGroupingSnapshot> findSavedByMatch(Long matchId) {
        MatchGrouping grouping = groupingMapper.selectSavedByMatch(matchId);
        if (grouping == null || grouping.getSavedAt() == null) {
            return Optional.empty();
        }
        Map<Long, Integer> playerTeams = groupingPlayerMapper
                .selectByGroupingId(grouping.getId()).stream()
                .filter(row -> row.getIsDeleted() == null || row.getIsDeleted() == 0)
                .collect(Collectors.toMap(
                        MatchGroupingPlayer::getPlayerId, MatchGroupingPlayer::getTeam));
        return Optional.of(new SavedGroupingSnapshot(
                matchId, grouping.getSavedAt(), playerTeams));
    }
}
