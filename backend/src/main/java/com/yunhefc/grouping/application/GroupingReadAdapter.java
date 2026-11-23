package com.yunhefc.grouping.application;

import com.yunhefc.contracts.grouping.GroupingReadPort;
import com.yunhefc.contracts.grouping.SavedGroupingSnapshot;
import com.yunhefc.grouping.domain.MatchGrouping;
import com.yunhefc.grouping.domain.MatchGroupingPlayer;
import com.yunhefc.grouping.infrastructure.MatchGroupingMapper;
import com.yunhefc.grouping.infrastructure.MatchGroupingPlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

    @Override
    public Map<Long, SavedGroupingSnapshot> findSavedByMatchIds(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Map.of();
        }
        List<MatchGrouping> groupings = groupingMapper.selectSavedByMatchIds(matchIds).stream()
                .filter(grouping -> grouping.getSavedAt() != null)
                .toList();
        if (groupings.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<MatchGroupingPlayer>> playersByGrouping = groupingPlayerMapper
                .selectByGroupingIds(groupings.stream().map(MatchGrouping::getId).toList()).stream()
                .filter(row -> row.getIsDeleted() == null || row.getIsDeleted() == 0)
                .collect(Collectors.groupingBy(MatchGroupingPlayer::getGroupingId));

        Map<Long, SavedGroupingSnapshot> result = new HashMap<>();
        for (MatchGrouping grouping : groupings) {
            Map<Long, Integer> playerTeams = playersByGrouping
                    .getOrDefault(grouping.getId(), List.of()).stream()
                    .collect(Collectors.toMap(
                            MatchGroupingPlayer::getPlayerId, MatchGroupingPlayer::getTeam));
            result.put(grouping.getMatchId(), new SavedGroupingSnapshot(
                    grouping.getMatchId(), grouping.getSavedAt(), playerTeams));
        }
        return result;
    }
}
