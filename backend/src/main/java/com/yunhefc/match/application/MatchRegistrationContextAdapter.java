package com.yunhefc.match.application;

import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MatchRegistrationContextAdapter implements MatchRegistrationContextPort {

    private final TeamMatchMapper teamMatchMapper;

    @Override
    public Optional<MatchSnapshot> lockForRegistration(Long matchId, Long teamId) {
        return Optional.ofNullable(teamMatchMapper.selectByIdForUpdate(matchId, teamId))
                .map(MatchReadAdapter::toSnapshot);
    }

    @Override
    public List<MatchSnapshot> lockNotStartedMatches(Long teamId) {
        return teamMatchMapper.lockNotStartedMatches(teamId).stream()
                .map(MatchReadAdapter::toSnapshot)
                .toList();
    }
}
