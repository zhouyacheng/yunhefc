package com.yunhefc.match.application;

import com.yunhefc.contracts.match.MatchRegistrationContextPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
