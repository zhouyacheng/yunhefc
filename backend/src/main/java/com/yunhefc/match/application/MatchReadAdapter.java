package com.yunhefc.match.application;

import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MatchReadAdapter implements MatchReadPort {

    private final TeamMatchMapper teamMatchMapper;

    public MatchReadAdapter(TeamMatchMapper teamMatchMapper) {
        this.teamMatchMapper = teamMatchMapper;
    }

    @Override
    public Optional<MatchSnapshot> findById(Long matchId) {
        return Optional.ofNullable(teamMatchMapper.selectById(matchId)).map(MatchReadAdapter::toSnapshot);
    }

    private static MatchSnapshot toSnapshot(TeamMatch match) {
        return new MatchSnapshot(
                match.getId(),
                match.getTeamId(),
                match.getTitle(),
                match.getStartAt(),
                match.getEndAt(),
                match.getVenue(),
                match.getStatus(),
                match.getHomeTeamName(),
                match.getAwayTeamName());
    }
}
