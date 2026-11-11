package com.yunhefc.match.application;

import com.yunhefc.contracts.match.MatchReadPort;
import com.yunhefc.contracts.match.MatchSnapshot;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MatchReadAdapter implements MatchReadPort {

    private final TeamMatchMapper teamMatchMapper;

    @Override
    public Optional<MatchSnapshot> findById(Long matchId) {
        return Optional.ofNullable(teamMatchMapper.selectById(matchId)).map(MatchReadAdapter::toSnapshot);
    }

    @Override
    public Optional<MatchSnapshot> findByIdForUpdate(Long matchId, Long teamId) {
        return Optional.ofNullable(teamMatchMapper.selectByIdForUpdate(matchId, teamId))
                .map(MatchReadAdapter::toSnapshot);
    }

    @Override
    public List<MatchSnapshot> findByIds(Collection<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return List.of();
        }
        return teamMatchMapper.selectBatchIds(matchIds).stream()
                .map(MatchReadAdapter::toSnapshot)
                .toList();
    }

    @Override
    public List<MatchSnapshot> findFinishedBefore(Instant cutoff) {
        return teamMatchMapper.selectFinishedBefore(MatchStatus.FINISHED.code(), cutoff).stream()
                .map(MatchReadAdapter::toSnapshot)
                .toList();
    }

    @Override
    public List<MatchSnapshot> findSeasonMatches(Long teamId, String season) {
        if (season == null || season.isBlank()) {
            return List.of();
        }
        // SQL 已按 season 预筛；空赛季行需按开赛年份在应用层二次裁决。
        return teamMatchMapper.selectSeasonMatches(teamId, season, MatchStatus.CANCELLED.code())
                .stream()
                .map(MatchReadAdapter::toSnapshot)
                .filter(match -> season.equals(match.effectiveSeason()))
                .toList();
    }

    static MatchSnapshot toSnapshot(TeamMatch match) {
        return new MatchSnapshot(
                match.getId(),
                match.getTeamId(),
                match.getTitle(),
                match.getStartAt(),
                match.getEndAt(),
                match.getSignupDeadline(),
                match.getCancelLock(),
                match.getCapacity(),
                match.getGuestCapacity(),
                match.getWaitlistCapacity(),
                match.getVenue(),
                match.getStatus().code(),
                match.getHomeTeamName(),
                match.getAwayTeamName(),
                match.getStatusUpdatedAt(),
                match.getSeason(),
                match.getType().code(),
                match.getOurSide() == null ? null : match.getOurSide().code());
    }
}
