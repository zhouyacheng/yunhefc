package com.yunhefc.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.activity.domain.ActivitySignup;
import com.yunhefc.activity.domain.SignupStatus;
import com.yunhefc.activity.infrastructure.ActivitySignupMapper;
import com.yunhefc.contracts.activity.ActivitySignupReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ActivitySignupReadAdapter implements ActivitySignupReadPort {

    private final ActivitySignupMapper activitySignupMapper;
    private final PlayerReadPort playerReadPort;

    @Override
    public List<Long> findActiveUserIds(Long teamId, Long activityId) {
        List<ActivitySignup> signups = activitySignupMapper.selectList(
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
                        .eq(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                        .orderByAsc(ActivitySignup::getSignedUpAt)
                        .orderByAsc(ActivitySignup::getId));
        if (signups.isEmpty()) {
            return List.of();
        }
        Map<Long, PlayerSnapshot> players = playerReadPort.findByIds(teamId,
                        signups.stream().map(ActivitySignup::getPlayerId).toList())
                .stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));
        return signups.stream()
                .map(signup -> players.get(signup.getPlayerId()))
                .filter(player -> player != null && player.userId() != null)
                .map(PlayerSnapshot::userId)
                .distinct()
                .toList();
    }
}
