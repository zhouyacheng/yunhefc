package com.yunhefc.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.activity.domain.ActivitySignup;
import com.yunhefc.activity.domain.SignupStatus;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupView;
import com.yunhefc.activity.infrastructure.ActivitySignupMapper;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.infra.storage.AvatarUrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 活动名单读取与行组装；C 端 ACT-05 与管理端 ACT-11 共用同一口径。
 */
@Component
@RequiredArgsConstructor
public class ActivityRosterAssembler {

    private final ActivitySignupMapper activitySignupMapper;
    private final PlayerReadPort playerReadPort;
    private final AvatarUrlSigner avatarUrlSigner;

    public PageResponse<ActivitySignupView> page(Long teamId, Long activityId, long page, long pageSize) {
        ActivityQueryParsing.validatePaging(page, pageSize);
        Page<ActivitySignup> result = activitySignupMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getActivityId, activityId)
                        .eq(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                        .orderByAsc(ActivitySignup::getSignedUpAt)
                        .orderByAsc(ActivitySignup::getId));
        List<Long> playerIds = result.getRecords().stream().map(ActivitySignup::getPlayerId).toList();
        Map<Long, PlayerSnapshot> players = playerReadPort.findByIds(teamId, playerIds).stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));
        List<ActivitySignupView> items = result.getRecords().stream()
                .map(signup -> toView(signup, players.get(signup.getPlayerId())))
                .toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

    public ActivitySignupView toView(ActivitySignup signup, PlayerSnapshot player) {
        return new ActivitySignupView(
                signup.getId(),
                signup.getActivityId(),
                signup.getPlayerId(),
                player == null ? "未知球员" : player.name(),
                player == null ? null : player.jersey(),
                player == null ? null : avatarUrlSigner.sign(player.avatarUrl()),
                Boolean.TRUE.equals(signup.getWithFamily()),
                signup.getFamilyNote(),
                signup.getStatus().code(),
                signup.getStatus().label(),
                signup.getSignedUpAt());
    }
}
