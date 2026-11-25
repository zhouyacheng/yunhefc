package com.yunhefc.activity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.activity.domain.ActivityRules;
import com.yunhefc.activity.domain.ActivitySignup;
import com.yunhefc.activity.domain.ActivityStatus;
import com.yunhefc.activity.domain.ActivityStatusMachine;
import com.yunhefc.activity.domain.ActivityType;
import com.yunhefc.activity.domain.SignupStatus;
import com.yunhefc.activity.domain.TeamActivity;
import com.yunhefc.activity.dto.ActivityDtos.ActivityAdmin;
import com.yunhefc.activity.dto.ActivityDtos.ActivityAdminQuery;
import com.yunhefc.activity.dto.ActivityDtos.ActivityCreateRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivityImageUploadView;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupRemoveResult;
import com.yunhefc.activity.dto.ActivityDtos.ActivitySignupView;
import com.yunhefc.activity.dto.ActivityDtos.ActivityStatusRequest;
import com.yunhefc.activity.dto.ActivityDtos.ActivityUpdateRequest;
import com.yunhefc.activity.infrastructure.ActivityImageRepository;
import com.yunhefc.activity.infrastructure.ActivitySignupCountRow;
import com.yunhefc.activity.infrastructure.ActivitySignupMapper;
import com.yunhefc.activity.infrastructure.TeamActivityMapper;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.common.util.DateRanges;
import com.yunhefc.common.util.LikePattern;
import com.yunhefc.common.util.Ulid;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.outbox.OutboxService;
import com.yunhefc.infra.storage.ImageSniffer;
import com.yunhefc.infra.storage.OssGateway;
import com.yunhefc.infra.storage.UploadLimits;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * B 端活动：创建、编辑、状态流转、名单与移除报名，以及 FIL-03 场地环境图片上传。
 */
@Service
@RequiredArgsConstructor
public class ActivityAdminApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ActivityAdminApplicationService.class);
    private static final String ACTIVITY_IMAGE_TOO_LARGE = "图片不能超过 5MB";

    private final TeamActivityMapper teamActivityMapper;
    private final ActivitySignupMapper activitySignupMapper;
    private final ActivityRosterAssembler rosterAssembler;
    private final ActivityImageRepository imageRepository;
    private final ActivitySignupLock signupLock;
    private final AuditRecorder auditRecorder;
    private final OutboxService outboxService;
    private final OssGateway ossGateway;

    @Transactional(readOnly = true)
    public PageResponse<ActivityAdmin> list(ActivityAdminQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        ActivityQueryParsing.validatePaging(query.page(), query.pageSize());
        String keyword = ActivityQueryParsing.keyword(query.keyword());
        DateRanges.Range dateRange = DateRanges.parse(query.startDate(), query.endDate());
        LambdaQueryWrapper<TeamActivity> wrapper = new LambdaQueryWrapper<TeamActivity>()
                .eq(TeamActivity::getTeamId, teamId)
                .eq(query.type() != null, TeamActivity::getType, ActivityQueryParsing.type(query.type()))
                .eq(query.status() != null, TeamActivity::getStatus, ActivityQueryParsing.status(query.status()));
        if (dateRange != null) {
            wrapper.ge(TeamActivity::getStartAt, dateRange.startAt())
                    .lt(TeamActivity::getStartAt, dateRange.endExclusiveAt());
        }
        wrapper.and(keyword != null, nested -> nested
                        .like(TeamActivity::getTitle, LikePattern.escape(keyword))
                        .or()
                        .like(TeamActivity::getLocation, LikePattern.escape(keyword)))
                .orderByDesc(TeamActivity::getStartAt)
                .orderByDesc(TeamActivity::getId);

        Page<TeamActivity> result = teamActivityMapper.selectPage(
                new Page<>(query.page(), query.pageSize()), wrapper);
        List<Long> activityIds = result.getRecords().stream().map(TeamActivity::getId).toList();
        Map<Long, Integer> counts = counts(activityIds);
        Map<Long, List<String>> images = imageRepository.keysByActivity(activityIds);
        List<ActivityAdmin> items = result.getRecords().stream()
                .map(activity -> toAdmin(activity, counts.getOrDefault(activity.getId(), 0),
                        images.getOrDefault(activity.getId(), List.of())))
                .toList();
        return PageResponse.of(items, result.getTotal(), query.page(), query.pageSize());
    }

    @Transactional(readOnly = true)
    public ActivityAdmin detail(Long activityId) {
        TeamActivity activity = requireActivity(CurrentUser.principal().teamId(), activityId);
        return toAdmin(activity, activitySignupMapper.countActive(activityId),
                imageRepository.keys(activityId));
    }

    @Transactional
    public ActivityAdmin create(ActivityCreateRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        TeamActivity activity = new TeamActivity();
        activity.setTeamId(principal.teamId());
        activity.setTitle(requiredText(request.title(), "title", "活动名称"));
        activity.setType(requiredType(request.type()));
        activity.setStartAt(request.startAt());
        activity.setEndAt(request.endAt());
        activity.setSignupDeadline(request.signupDeadline());
        activity.setCapacity(request.capacity() == null
                ? ActivityRules.DEFAULT_CAPACITY : request.capacity());
        activity.setFamilyAllowed(Boolean.TRUE.equals(request.familyAllowed()));
        activity.setCostPerPerson(request.costPerPerson());
        activity.setLocation(requiredText(request.location(), "location", "活动地址"));
        activity.setDescription(optionalText(request.description()));
        ActivityRules.validateSchedule(activity.getStartAt(), activity.getEndAt(), activity.getSignupDeadline());
        ActivityRules.validateCapacity(activity.getCapacity());
        ActivityRules.validateCost(activity.getCostPerPerson());
        ActivityRules.validateImageKeys(request.locationImageKeys());
        activity.setStatus(ActivityStatus.SIGNUP_OPEN);
        activity.setVersion(0);
        activity.setCreatedBy(principal.userId());

        teamActivityMapper.insert(activity);
        imageRepository.replace(activity.getId(), request.locationImageKeys());
        auditRecorder.recordCurrent("ACTIVITY_CREATE", "ACTIVITY", activity.getId(),
                null, ActivityAuditSnapshot.of(activity, imageRepository.keys(activity.getId())));
        outboxService.append(activity.getTeamId(), "ACTIVITY", activity.getId(),
                EventTypes.ACTIVITY_CREATED, Map.of(
                        "activityId", activity.getId(),
                        "activityTitle", activity.getTitle(),
                        "startAt", activity.getStartAt(),
                        "location", activity.getLocation()));
        log.info("activity created, activityId={}, teamId={}", activity.getId(), activity.getTeamId());
        return toAdmin(activity, 0, imageRepository.keys(activity.getId()));
    }

    @Transactional
    public ActivityAdmin update(Long activityId, ActivityUpdateRequest request) {
        rejectUnknownFields(request);
        Long teamId = CurrentUser.principal().teamId();
        TeamActivity activity = requireActivityForUpdate(teamId, activityId);
        if (!request.getVersion().equals(activity.getVersion())) {
            throw versionConflict(activity.getVersion());
        }
        // 只有调低上限需要与报名「计数+写入」串行；调高不可能违反 count<=capacity。
        boolean capacityShrink = request.hasCapacity() && request.getCapacity() != null
                && (activity.getCapacity() == null
                || request.getCapacity() < activity.getCapacity());
        if (capacityShrink) {
            signupLock.acquire(activityId);
            int signupCount = activitySignupMapper.countActive(activityId);
            if (request.getCapacity() < signupCount) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "人数上限不得低于当前有效报名数");
            }
        }
        Map<String, Object> before = ActivityAuditSnapshot.of(activity, imageRepository.keys(activityId));
        applyChanges(activity, request);

        int updated = teamActivityMapper.update(null, new LambdaUpdateWrapper<TeamActivity>()
                .eq(TeamActivity::getId, activityId)
                .eq(TeamActivity::getTeamId, teamId)
                .eq(TeamActivity::getVersion, request.getVersion())
                .set(TeamActivity::getTitle, activity.getTitle())
                .set(TeamActivity::getType, activity.getType())
                .set(TeamActivity::getStartAt, activity.getStartAt())
                .set(TeamActivity::getEndAt, activity.getEndAt())
                .set(TeamActivity::getSignupDeadline, activity.getSignupDeadline())
                .set(TeamActivity::getCapacity, activity.getCapacity())
                .set(TeamActivity::isFamilyAllowed, activity.isFamilyAllowed())
                .set(TeamActivity::getCostPerPerson, activity.getCostPerPerson())
                .set(TeamActivity::getLocation, activity.getLocation())
                .set(TeamActivity::getDescription, activity.getDescription())
                .set(TeamActivity::getVersion, activity.getVersion() + 1));
        TeamActivity saved = reloadAfterWrite(updated, teamId, activityId, null);
        if (request.hasLocationImageKeys()) {
            imageRepository.replace(activityId, request.getLocationImageKeys());
        }
        List<String> afterImages = imageRepository.keys(activityId);
        auditRecorder.recordCurrent("ACTIVITY_UPDATE", "ACTIVITY", activityId,
                before, ActivityAuditSnapshot.of(saved, afterImages));
        log.info("activity updated, activityId={}", activityId);
        return toAdmin(saved, activitySignupMapper.countActive(activityId), afterImages);
    }

    @Transactional
    public ActivityAdmin changeStatus(Long activityId, ActivityStatusRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        ActivityStatus target = ActivityStatus.of(request.status());
        String reason = target == ActivityStatus.CANCELLED
                ? optionalText(request.reason())
                : null;
        TeamActivity activity = requireActivityForUpdate(principal.teamId(), activityId);
        ActivityStatusMachine.requireTransition(activity.getStatus(), target);
        if (!request.version().equals(activity.getVersion())) {
            throw versionConflict(activity.getVersion());
        }
        // 取消与报名串行化：取消提交后不得再写入有效报名，避免漏发取消通知。
        if (target == ActivityStatus.CANCELLED) {
            signupLock.acquire(activityId);
        }
        List<String> imageKeys = imageRepository.keys(activityId);
        Map<String, Object> before = ActivityAuditSnapshot.of(activity, imageKeys);

        LambdaUpdateWrapper<TeamActivity> update = new LambdaUpdateWrapper<TeamActivity>()
                .eq(TeamActivity::getId, activityId)
                .eq(TeamActivity::getTeamId, principal.teamId())
                .eq(TeamActivity::getVersion, request.version())
                .eq(TeamActivity::getStatus, activity.getStatus())
                .set(TeamActivity::getStatus, target)
                .set(TeamActivity::getVersion, activity.getVersion() + 1);
        if (target == ActivityStatus.CANCELLED) {
            update.set(TeamActivity::getCancelReason, reason);
        }
        int updated = teamActivityMapper.update(null, update);
        TeamActivity saved = reloadAfterWrite(updated, principal.teamId(), activityId, target);
        auditRecorder.recordCurrent("ACTIVITY_STATUS_CHANGE", "ACTIVITY", activityId,
                before, ActivityAuditSnapshot.of(saved, imageKeys));
        if (target == ActivityStatus.CANCELLED) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("activityId", activityId);
            payload.put("activityTitle", saved.getTitle());
            if (saved.getCancelReason() != null) {
                payload.put("reason", saved.getCancelReason());
            }
            outboxService.append(saved.getTeamId(), "ACTIVITY", activityId,
                    EventTypes.ACTIVITY_CANCELLED, payload);
        }
        log.info("activity status changed, activityId={}, from={}, to={}",
                activityId, before.get("status"), target.code());
        return toAdmin(saved, activitySignupMapper.countActive(activityId), imageKeys);
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivitySignupView> roster(Long activityId, long page, long pageSize) {
        Long teamId = CurrentUser.principal().teamId();
        requireActivity(teamId, activityId);
        return rosterAssembler.page(teamId, activityId, page, pageSize);
    }

    /**
     * 管理员移除与本人取消同一落库语义：更新原行 status=1，不伪删除；重复移除返回 false。
     */
    @Transactional
    public ActivitySignupRemoveResult removeSignup(Long activityId, Long signupId) {
        AuthPrincipal principal = CurrentUser.principal();
        TeamActivity activity = requireActivityForShare(principal.teamId(), activityId);
        ActivityStatusMachine.requireRemovableSignup(activity.getStatus());
        ActivitySignup signup = activitySignupMapper.selectOne(new LambdaQueryWrapper<ActivitySignup>()
                .eq(ActivitySignup::getId, signupId)
                .eq(ActivitySignup::getActivityId, activityId));
        if (signup == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报名不存在");
        }
        if (signup.getStatus() == SignupStatus.CANCELLED) {
            return new ActivitySignupRemoveResult(false);
        }
        LocalDateTime now = ShanghaiTime.now();
        int updated = activitySignupMapper.update(null, new LambdaUpdateWrapper<ActivitySignup>()
                .eq(ActivitySignup::getId, signupId)
                .eq(ActivitySignup::getStatus, SignupStatus.CONFIRMED)
                .set(ActivitySignup::getStatus, SignupStatus.CANCELLED)
                .set(ActivitySignup::getCancelledAt, now)
                .set(ActivitySignup::getCancelledBy, principal.userId()));
        if (updated == 0) {
            return new ActivitySignupRemoveResult(false);
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("activityId", activityId);
        before.put("playerId", signup.getPlayerId());
        before.put("status", signup.getStatus().code());
        before.put("signedUpAt", signup.getSignedUpAt());
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("status", SignupStatus.CANCELLED.code());
        after.put("cancelledAt", now);
        after.put("cancelledBy", principal.userId());
        auditRecorder.recordCurrent("ACTIVITY_SIGNUP_REMOVE", "ACTIVITY_SIGNUP", signupId, before, after);
        log.info("activity signup removed, activityId={}, signupId={}, playerId={}",
                activityId, signupId, signup.getPlayerId());
        return new ActivitySignupRemoveResult(true);
    }

    public ActivityImageUploadView uploadImage(MultipartFile file) {
        AuthPrincipal principal = CurrentUser.principal();
        byte[] content = read(file);
        if (content.length > UploadLimits.ACTIVITY_IMAGE_MAX_BYTES) {
            throw invalidFile(ACTIVITY_IMAGE_TOO_LARGE);
        }
        ImageSniffer.Format format = ImageSniffer.sniff(content);
        String objectKey = "activity/" + principal.userId() + "/" + Ulid.generate()
                + "." + format.extension();
        try {
            ossGateway.putObject(objectKey, content, format.contentType());
        } catch (RuntimeException ex) {
            log.warn("activity image upload failed, objectKey={}", objectKey, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new ActivityImageUploadView(objectKey, ossGateway.signedUrl(objectKey));
    }

    private void applyChanges(TeamActivity activity, ActivityUpdateRequest request) {
        if (request.getTitle() != null) {
            activity.setTitle(requiredText(request.getTitle(), "title", "活动名称"));
        }
        if (request.getType() != null) {
            activity.setType(requiredType(request.getType()));
        }
        if (request.getStartAt() != null) {
            activity.setStartAt(request.getStartAt());
        }
        if (request.hasEndAt()) {
            activity.setEndAt(request.getEndAt());
        }
        if (request.getSignupDeadline() != null) {
            activity.setSignupDeadline(request.getSignupDeadline());
        }
        if (request.hasCapacity()) {
            activity.setCapacity(request.getCapacity());
        }
        if (request.hasFamilyAllowed()) {
            activity.setFamilyAllowed(Boolean.TRUE.equals(request.getFamilyAllowed()));
        }
        if (request.hasCostPerPerson()) {
            activity.setCostPerPerson(request.getCostPerPerson());
        }
        if (request.getLocation() != null) {
            activity.setLocation(requiredText(request.getLocation(), "location", "活动地址"));
        }
        if (request.hasLocationImageKeys()) {
            ActivityRules.validateImageKeysPresent(request.getLocationImageKeys());
        }
        if (request.hasDescription()) {
            activity.setDescription(optionalText(request.getDescription()));
        }
        ActivityRules.validateSchedule(activity.getStartAt(), activity.getEndAt(), activity.getSignupDeadline());
        ActivityRules.validateCapacity(activity.getCapacity());
        ActivityRules.validateCost(activity.getCostPerPerson());
    }

    /**
     * 写入失败后重新读取：状态流转非法返回 40917，否则按版本冲突 40901。
     * 字段编辑不受状态限制（P-113），失败只可能是版本冲突。
     */
    private TeamActivity reloadAfterWrite(int updated, Long teamId, Long activityId,
                                          ActivityStatus target) {
        TeamActivity current = teamActivityMapper.selectByIdForUpdate(activityId, teamId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        if (updated == 0) {
            if (target != null) {
                ActivityStatusMachine.requireTransition(current.getStatus(), target);
            }
            throw versionConflict(current.getVersion());
        }
        return current;
    }

    private TeamActivity requireActivity(Long teamId, Long activityId) {
        TeamActivity activity = teamActivityMapper.selectOne(new LambdaQueryWrapper<TeamActivity>()
                .eq(TeamActivity::getId, activityId)
                .eq(TeamActivity::getTeamId, teamId));
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private TeamActivity requireActivityForUpdate(Long teamId, Long activityId) {
        TeamActivity activity = teamActivityMapper.selectByIdForUpdate(activityId, teamId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private TeamActivity requireActivityForShare(Long teamId, Long activityId) {
        TeamActivity activity = teamActivityMapper.selectByIdForShare(activityId, teamId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private Map<Long, Integer> counts(List<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        return activitySignupMapper.selectActiveCounts(activityIds).stream()
                .collect(Collectors.toMap(ActivitySignupCountRow::getActivityId,
                        ActivitySignupCountRow::getSignupCount));
    }

    private ActivityAdmin toAdmin(TeamActivity activity, int signupCount, List<String> imageKeys) {
        return new ActivityAdmin(
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
                imageKeys,
                imageKeys.stream().map(ossGateway::signedUrl).toList(),
                activity.getDescription(),
                activity.getStatus().code(),
                activity.getStatus().label(),
                signupCount,
                ActivityRules.signupOpen(activity, signupCount, ShanghaiTime.now()),
                activity.getVersion(),
                activity.getCreatedBy());
    }

    private void rejectUnknownFields(ActivityUpdateRequest request) {
        if (request.getUnknownFields().isEmpty()) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        for (String field : request.getUnknownFields()) {
            details.put(field, "不可修改字段");
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(), Map.of("details", details));
    }

    private ActivityType requiredType(Integer code) {
        try {
            return ActivityType.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "活动类型不合法");
        }
    }

    private String requiredText(String value, String field, String label) {
        String normalized = ActivityQueryParsing.normalize(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, label + "不能为空",
                    Map.of("details", Map.of(field, "不能为空")));
        }
        return normalized;
    }

    private String optionalText(String value) {
        return ActivityQueryParsing.normalize(value);
    }

    private BusinessException versionConflict(int currentVersion) {
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "活动已被他人修改，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalidFile(ImageSniffer.EMPTY_MESSAGE);
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw invalidFile(ImageSniffer.CORRUPT_MESSAGE);
        }
    }

    private BusinessException invalidFile(String reason) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(), Map.of("details", Map.of("file", reason)));
    }
}
