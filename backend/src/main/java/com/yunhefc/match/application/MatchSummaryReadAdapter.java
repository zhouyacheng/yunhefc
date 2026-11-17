package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.event.DerivedScoreSnapshot;
import com.yunhefc.contracts.event.EventScoreReadPort;
import com.yunhefc.contracts.match.MatchSummaryReadPort;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MatchSummaryReadAdapter implements MatchSummaryReadPort {

    private final TeamMatchMapper teamMatchMapper;
    private final EventScoreReadPort eventScoreReadPort;

    @Override
    public Optional<MatchSummarySnapshot> findSummary(Long matchId, Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teamMatchMapper.selectOne(new LambdaQueryWrapper<TeamMatch>()
                        .eq(TeamMatch::getId, matchId)
                        .eq(TeamMatch::getTeamId, teamId)))
                .map(match -> MatchSummaryMapping.toSummary(match,
                        eventScoreReadPort.findDerivedScore(matchId).orElse(null)));
    }

    @Override
    public Map<Long, MatchSummarySnapshot> findSummaries(Collection<Long> matchIds, Long teamId) {
        if (matchIds == null || matchIds.isEmpty() || teamId == null) {
            return Map.of();
        }
        Map<Long, DerivedScoreSnapshot> derivedScores = eventScoreReadPort.findDerivedScores(matchIds);
        return teamMatchMapper.selectList(new LambdaQueryWrapper<TeamMatch>()
                        .in(TeamMatch::getId, matchIds)
                        .eq(TeamMatch::getTeamId, teamId))
                .stream()
                .map(match -> MatchSummaryMapping.toSummary(match, derivedScores.get(match.getId())))
                .collect(Collectors.toMap(MatchSummarySnapshot::id, Function.identity()));
    }
}
