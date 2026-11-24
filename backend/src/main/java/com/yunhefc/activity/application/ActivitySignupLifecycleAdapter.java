package com.yunhefc.activity.application;

import com.yunhefc.activity.infrastructure.ActivitySignupMapper;
import com.yunhefc.contracts.activity.ActivitySignupLifecyclePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ActivitySignupLifecycleAdapter implements ActivitySignupLifecyclePort {

    private final ActivitySignupMapper activitySignupMapper;

    @Override
    @Transactional
    public void cancelActiveForDisabledPlayer(Long teamId, Long playerId, Long cancelledBy) {
        activitySignupMapper.cancelActiveForDisabledPlayer(teamId, playerId, cancelledBy);
    }
}
