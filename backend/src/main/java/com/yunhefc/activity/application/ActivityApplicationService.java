package com.yunhefc.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.activity.domain.ActivityRules;
import com.yunhefc.activity.domain.ActivitySignup;
import com.yunhefc.activity.domain.ActivityStatus;
import com.yunhefc.activity.domain.SignupStatus;
import com.yunhefc.activity.domain.TeamActivity;
import com.yunhefc.activity.dto.ActivityDtos.ActivityCancelResult;
import com.yunhefc.activity.dto.ActivityDtos.ActivityQuery;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupResponse;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupView;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySummary;
import com.yunhefc.activity.infrastructure.ActivityImageRepository;
import com.yunhefc.activity.infrastructure.ActivitySignupCountRow;
import com.yunhefc.activity.infrastructure.ActivitySignupMapper;
import com.yunhefc.activity.infrastructure.TeamActivityMapper;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.infra.storage.OssGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * C 端活动：公开列表与详情、本人报名/取消、报名名单。
 */
@Service
@RequiredArgsConstructor
public class ActivityApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ActivityApplicationService.class);

    private final TeamActivityMapper teamActivityMapper;
    private final ActivitySignupMapper activitySignupMapper;
    private final TeamReadPort teamReadPort;
    private final PlayerReadPort playerReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final ActivityRosterAssembler rosterAssembler;
    private final ActivityImageRepository imageRepository;
    private final OssGateway ossGateway;
    private final ActivitySignupLock signupLock;

    @Transactional(readOnly = true)
    public PageResponse<ActivitySummary> list(ActivityQuery query) {
        Long teamId = teamReadPort.currentTeamId();
        String scope = ActivityQueryParsing.scope(query.scope());
        LambdaQueryWrapper<TeamActivity> wrapper = new LambdaQueryWrapper<TeamActivity>()
                .eq(TeamActivity::getTeamId, teamId)
                .eq(query.type() != null, TeamActivity::getType, ActivityQueryParsing.type(query.type()))
                .eq(query.status() != null, TeamActivity::getStatus, ActivityQueryParsing.status(query.status()));
        LocalDateTime now = ShanghaiTime.now();
        if (ActivityQueryParsing.SCOPE_HISTORY.equals(scope)) {
            wrapper.lt(TeamActivity::getStartAt, now);
        }
        if (ActivityQueryParsing.SCOPE_LATEST.equals(scope)) {
            // P-117：最近活动 = 所有报名中活动（不论是否已过截止或满员），沿用分页与排序。
            wrapper.eq(TeamActivity::getStatus, ActivityStatus.SIGNUP_OPEN);
        }

        ActivityQueryParsing.validatePaging(query.page(), query.pageSize());
        if (ActivityQueryParsing.ascending(query.sort())) {
            wrapper.orderByAsc(TeamActivity::getStartAt).orderByAsc(TeamActivity::getId);
        } else {
            // 与管理端一致：同一开始时间按 id 倒序取最新。
            wrapper.orderByDesc(TeamActivity::getStartAt).orderByDesc(TeamActivity::getId);
        }
        Page<TeamActivity> result = teamActivityMapper.selectPage(
                new Page<>(query.page(), query.pageSize()), wrapper);
        return PageResponse.of(toSummaries(result.getRecords(), now),
                result.getTotal(), query.page(), query.pageSize());
    }

    @Transactional(readOnly = true)
    public ActivitySummary detail(Long activityId) {
        TeamActivity activity = findActivity(teamReadPort.currentTeamId(), activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        LocalDateTime now = ShanghaiTime.now();
        int signupCount = activitySignupMapper.countActive(activityId);
        ActivitySignupView mine = CurrentUser.optionalPrincipal()
                .flatMap(principal -> mySignup(activityId, principal))
                .orElse(null);
        return toSummary(activity, signupCount, mine, imageRepository.keys(activityId), now);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivitySignupView> roster(Long activityId, long page, long pageSize) {
        Long teamId = CurrentUser.principal().teamId();
        if (findActivity(teamId, activityId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return rosterAssembler.page(teamId, activityId, page, pageSize);
    }

    /**
     * 报名在活动维度 Redis 锁内计数并写入；锁在事务提交/回滚后释放，避免释放先于提交导致超卖。
     */
    @Transactional
    public ActivitySignupResponse signup(Long activityId, ActivitySignupRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        // 共享锁账号：注销编排持有排他锁，提交后报名必然读到 DISABLED，避免在途报名写入。
        requireActiveAccount(principal);
        signupLock.acquire(activityId);

        TeamActivity activity = findActivity(principal.teamId(), activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        PlayerSnapshot player = requireEligiblePlayer(principal);
        LocalDateTime now = ShanghaiTime.now();
        if (!ActivityRules.withinSignupWindow(activity, now)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT, "活动当前不可报名");
        }
        // 已报名优先于满员与家属门禁：重复提交应得到 40916。
        ActivitySignup existing = activitySignupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getPlayerId, player.id())
                .last("LIMIT 1"));
        if (existing != null && existing.getStatus() == SignupStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ACTIVITY_SIGNUP_CONFLICT);
        }
        ActivityRules.validateFamilyAllowed(activity, Boolean.TRUE.equals(request.withFamily()));
        int signupCount = activitySignupMapper.countActive(activityId);
        if (signupCount >= activity.getCapacity()) {
            throw new BusinessException(ErrorCode.ACTIVITY_FULL);
        }
        ActivitySignup signup = existing == null
                ? insertSignup(activityId, player, request, now)
                : reactivateSignup(existing, request, now);
        log.info("activity signup created, activityId={}, signupId={}, playerId={}",
                activityId, signup.getId(), player.id());
        return new ActivitySignupResponse(rosterAssembler.toView(signup, player));
    }

    @Transactional
    public ActivityCancelResult cancel(Long activityId) {
        AuthPrincipal principal = CurrentUser.principal();
        TeamActivity activity = requireActivityForShare(principal.teamId(), activityId);
        LocalDateTime now = ShanghaiTime.now();
        if (!ActivityRules.withinSignupWindow(activity, now)) {
            throw new BusinessException(ErrorCode.ACTIVITY_STATE_CONFLICT, "活动当前不可取消报名");
        }
        Optional<PlayerSnapshot> player = playerReadPort.findByUserId(principal.userId());
        if (player.isEmpty()) {
            return new ActivityCancelResult(false);
        }
        ActivitySignup signup = activitySignupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getActivityId, activityId)
                .eq(ActivitySignup::getPlayerId, player.get().id())
                .last("LIMIT 1"));
        if (signup == null || signup.getStatus() == SignupStatus.CANCELLED) {
            return new ActivityCancelResult(false);
        }
        int updated = activitySignupMapper.update(null, new LambdaUpdateWrapper<ActivitySignup>()
                .eq(ActivitySignup::getId, signup.getId())
                .eq(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                .set(ActivitySignup::getStatus, SignupStatus.CANCELLED)
                .set(ActivitySignup::getCancelledAt, now)
                .set(ActivitySignup::getCancelledBy, principal.userId()));
        if (updated == 0) {
            return new ActivityCancelResult(false);
        }
        log.info("activity signup cancelled, activityId={}, signupId={}, playerId={}",
                activityId, signup.getId(), player.get().id());
        return new ActivityCancelResult(true);
    }

    private ActivitySignup insertSignup(Long activityId, PlayerSnapshot player,
                                        ActivitySignupRequest request, LocalDateTime now) {
        ActivitySignup signup = new ActivitySignup();
        signup.setActivityId(activityId);
        signup.setPlayerId(player.id());
        signup.setWithFamily(Boolean.TRUE.equals(request.withFamily()));
        signup.setFamilyNote(ActivityQueryParsing.normalize(request.familyNote()));
        signup.setStatus(SignupStatus.CONFIRMED);
        signup.setSignedUpAt(now);
        try {
            activitySignupMapper.insert(signup);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.ACTIVITY_SIGNUP_CONFLICT);
        }
        return signup;
    }

    /** 重新报名复用原行，写回 status=0 并清空取消信息；不保留历史行。 */
    private ActivitySignup reactivateSignup(ActivitySignup signup, ActivitySignupRequest request,
                                            LocalDateTime now) {
        activitySignupMapper.update(null, new LambdaUpdateWrapper<ActivitySignup>()
                .eq(ActivitySignup::getId, signup.getId())
                .set(ActivitySignup::getWithFamily, Boolean.TRUE.equals(request.withFamily()))
                .set(ActivitySignup::getFamilyNote, ActivityQueryParsing.normalize(request.familyNote()))
                .set(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                .set(ActivitySignup::getSignedUpAt, now)
                .set(ActivitySignup::getCancelledAt, null)
                .set(ActivitySignup::getCancelledBy, null));
        signup.setWithFamily(Boolean.TRUE.equals(request.withFamily()));
        signup.setFamilyNote(ActivityQueryParsing.normalize(request.familyNote()));
        signup.setStatus(SignupStatus.CONFIRMED);
        signup.setSignedUpAt(now);
        signup.setCancelledAt(null);
        signup.setCancelledBy(null);
        return signup;
    }

    /**
     * 仅正式球员可报名；外挂（GUEST）不可报名，与比赛报名口径刻意不同。
     * 管理员资格由权限码与 V17 授权决定，不按角色码硬编码。
     */
    private PlayerSnapshot requireEligiblePlayer(AuthPrincipal principal) {
        PlayerSnapshot player = playerReadPort.findByUserId(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "仅正式球员可报名活动"));
        if (player.type() != PlayerType.PLAYER.code()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅正式球员可报名活动");
        }
        return player;
    }

    private void requireActiveAccount(AuthPrincipal principal) {
        boolean active = userAccountReadPort.lockActiveForRegistration(principal.userId())
                .orElse(false);
        if (!active) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号不可用");
        }
    }

    private TeamActivity findActivity(Long teamId, Long activityId) {
        return teamActivityMapper.selectOne(new LambdaQueryWrapper<TeamActivity>()
                .eq(TeamActivity::getId, activityId)
                .eq(TeamActivity::getTeamId, teamId));
    }

    private TeamActivity requireActivityForShare(Long teamId, Long activityId) {
        TeamActivity activity = teamActivityMapper.selectByIdForShare(activityId, teamId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private Optional<ActivitySignupView> mySignup(Long activityId, AuthPrincipal principal) {
        return playerReadPort.findByUserId(principal.userId())
                .flatMap(player -> Optional.ofNullable(activitySignupMapper.selectOne(
                                new LambdaQueryWrapper<ActivitySignup>()
                                        .eq(ActivitySignup::getActivityId, activityId)
                                        .eq(ActivitySignup::getPlayerId, player.id())
                                        .eq(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                                        .last("LIMIT 1")))
                        .map(signup -> rosterAssembler.toView(signup, player)));
    }

    private List<ActivitySummary> toSummaries(List<TeamActivity> activities, LocalDateTime now) {
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> activityIds = activities.stream().map(TeamActivity::getId).toList();
        Map<Long, Integer> counts = activitySignupMapper.selectActiveCounts(activityIds).stream()
                .collect(Collectors.toMap(ActivitySignupCountRow::getActivityId,
                        ActivitySignupCountRow::getSignupCount));
        Map<Long, ActivitySignupView> mine = mySignups(activityIds);
        Map<Long, List<String>> images = imageRepository.keysByActivity(activityIds);
        return activities.stream()
                .map(activity -> toSummary(activity,
                        counts.getOrDefault(activity.getId(), 0),
                        mine.get(activity.getId()),
                        images.getOrDefault(activity.getId(), List.of()), now))
                .toList();
    }

    private Map<Long, ActivitySignupView> mySignups(Collection<Long> activityIds) {
        Optional<AuthPrincipal> principal = CurrentUser.optionalPrincipal();
        if (principal.isEmpty()) {
            return Map.of();
        }
        Optional<PlayerSnapshot> player = playerReadPort.findByUserId(principal.get().userId());
        if (player.isEmpty()) {
            return Map.of();
        }
        return activitySignupMapper.selectList(new LambdaQueryWrapper<ActivitySignup>()
                        .eq(ActivitySignup::getPlayerId, player.get().id())
                        .eq(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                        .in(ActivitySignup::getActivityId, activityIds))
                .stream()
                .collect(Collectors.toMap(ActivitySignup::getActivityId,
                        signup -> rosterAssembler.toView(signup, player.get()), (left, right) -> left));
    }

    private ActivitySummary toSummary(TeamActivity activity, int signupCount,
                                      ActivitySignupView mine, List<String> imageKeys,
                                      LocalDateTime now) {
        return new ActivitySummary(
                activity.getId(),
                activity.getTitle(),
                activity.getType().code(),
                activity.getType().label(),
                activity.getStartAt(),
                activity.getEndAt(),
                activity.getSignupDeadline(),
                activity.getCapacity(),
                activity.isFamilyAllowed(),
                activity.getCostPerPerson(),
                activity.getLocation(),
                imageKeys.stream().map(ossGateway::signedUrl).toList(),
                activity.getDescription(),
                activity.getStatus().code(),
                activity.getStatus().label(),
                signupCount,
                ActivityRules.signupOpen(activity, signupCount, now),
                activity.getVersion(),
                mine);
    }
}
