package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Override
    public Optional<MatchSummarySnapshot> findSummary(Long matchId, Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(teamMatchMapper.selectOne(new LambdaQueryWrapper<TeamMatch>()
                        .eq(TeamMatch::getId, matchId)
                        .eq(TeamMatch::getTeamId, teamId)))
                .map(MatchSummaryMapping::toSummary);
    }

    @Override
    public Map<Long, MatchSummarySnapshot> findSummaries(Collection<Long> matchIds, Long teamId) {
        if (matchIds == null || matchIds.isEmpty() || teamId == null) {
            return Map.of();
        }
        return teamMatchMapper.selectList(new LambdaQueryWrapper<TeamMatch>()
                        .in(TeamMatch::getId, matchIds)
                        .eq(TeamMatch::getTeamId, teamId))
                .stream()
                .map(MatchSummaryMapping::toSummary)
                .collect(Collectors.toMap(MatchSummarySnapshot::id, Function.identity()));
    }
}
