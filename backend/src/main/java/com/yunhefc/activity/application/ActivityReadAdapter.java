package com.yunhefc.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.activity.domain.TeamActivity;
import com.yunhefc.activity.infrastructure.TeamActivityMapper;
import com.yunhefc.contracts.activity.ActivityReadPort;
import com.yunhefc.contracts.activity.ActivitySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ActivityReadAdapter implements ActivityReadPort {

    private final TeamActivityMapper teamActivityMapper;

    @Override
    public Optional<ActivitySnapshot> findById(Long teamId, Long activityId) {
        return Optional.ofNullable(teamActivityMapper.selectOne(new LambdaQueryWrapper<TeamActivity>()
                        .eq(TeamActivity::getId, activityId)
                        .eq(TeamActivity::getTeamId, teamId)))
                .map(ActivityReadAdapter::toSnapshot);
    }

    @Override
    public Map<Long, ActivitySnapshot> findByIds(Long teamId, Collection<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Map.of();
        }
        return teamActivityMapper.selectList(new LambdaQueryWrapper<TeamActivity>()
                        .eq(TeamActivity::getTeamId, teamId)
                        .in(TeamActivity::getId, activityIds))
                .stream()
                .map(ActivityReadAdapter::toSnapshot)
                .collect(Collectors.toMap(ActivitySnapshot::id, Function.identity()));
    }

    private static ActivitySnapshot toSnapshot(TeamActivity activity) {
        return new ActivitySnapshot(
                activity.getId(), activity.getTeamId(), activity.getTitle(), activity.getStartAt());
    }
}
