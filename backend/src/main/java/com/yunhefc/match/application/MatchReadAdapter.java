package com.yunhefc.match.application;

import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MatchReadAdapter implements MatchReadPort {

    private final TeamMatchMapper teamMatchMapper;

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
                match.getStatus().code(),
                match.getHomeTeamName(),
                match.getAwayTeamName());
    }
}
