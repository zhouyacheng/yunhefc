package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.registration.RegistrationQueueCounts;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.settings.RegistrationDefaults;
import com.yunhefc.contracts.settings.RegistrationDefaultsPort;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchStatusMachine;
import com.yunhefc.match.domain.MatchTimeRules;
import com.yunhefc.match.domain.MatchType;
import com.yunhefc.match.domain.OurSide;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.dto.MatchDtos.MatchAdminQuery;
import com.yunhefc.match.dto.MatchDtos.MatchAdminView;
import com.yunhefc.match.dto.MatchDtos.MatchCancelRequest;
import com.yunhefc.match.dto.MatchDtos.MatchCreateRequest;
import com.yunhefc.match.dto.MatchDtos.MatchScheduleRequest;
import com.yunhefc.match.dto.MatchDtos.MatchStatusRequest;
import com.yunhefc.match.dto.MatchDtos.MatchUpdateRequest;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MatchAdminApplicationService {

    private static final int CREATE_ATTEMPTS = 20;
    private static final String DEFAULT_FORMAT = "8人制";
    private static final String MATCH_EDIT_PERMISSION = "match:edit:any";
    private static final String OUR_TEAM_MARKER = "云河FC";

    private final TeamMatchMapper teamMatchMapper;
    private final RegistrationDefaultsPort registrationDefaultsPort;
    private final RegistrationReadPort registrationReadPort;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public MatchAdminView create(MatchCreateRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        TeamMatch prepared = prepare(teamId, request);
        for (int attempt = 1; attempt <= CREATE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> persist(teamId, prepared));
            } catch (DuplicateKeyException | DeadlockLoserDataAccessException
                     | CannotAcquireLockException ex) {
                if (attempt == CREATE_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "场次编号冲突，请重试");
                }
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    @Transactional(readOnly = true)
    public PageResponse<MatchAdminView> list(MatchAdminQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        long page = query.page();
        long pageSize = query.pageSize();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页参数不合法");
        }
        MatchStatus status = MatchQueryParsing.status(query.status());
        MatchType type = MatchQueryParsing.type(query.type());
        String season = MatchQueryParsing.seasonFilter(query.season());
        String scope = MatchQueryParsing.scope(query.scope());
        boolean ascending = MatchQueryParsing.ascending(query.sort());

        LambdaQueryWrapper<TeamMatch> wrapper = new LambdaQueryWrapper<TeamMatch>()
                .eq(TeamMatch::getTeamId, teamId)
                .eq(status != null, TeamMatch::getStatus, status)
                .eq(type != null, TeamMatch::getType, type)
                .eq(season != null, TeamMatch::getSeason, season);
        if (MatchQueryParsing.SCOPE_UPCOMING.equals(scope)) {
            wrapper.notIn(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        } else if (MatchQueryParsing.SCOPE_HISTORY.equals(scope)) {
            wrapper.in(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        }
        if (ascending) {
            wrapper.orderByAsc(TeamMatch::getStartAt);
        } else {
            wrapper.orderByDesc(TeamMatch::getStartAt);
        }
        wrapper.orderByAsc(TeamMatch::getId);

        Page<TeamMatch> result = teamMatchMapper.selectPage(new Page<>(page, pageSize), wrapper);
        Map<Long, RegistrationQueueCounts> counts = registrationReadPort.queueCounts(
                result.getRecords().stream().map(TeamMatch::getId).toList());
        List<MatchAdminView> items = result.getRecords().stream()
                .map(match -> toView(match,
                        counts.getOrDefault(match.getId(), RegistrationQueueCounts.EMPTY)))
                .toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public MatchAdminView detail(Long matchId) {
        Long teamId = CurrentUser.principal().teamId();
        TeamMatch match = teamMatchMapper.selectOne(new LambdaQueryWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId));
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        return toView(match, counts(matchId));
    }

    @Transactional
    public MatchAdminView update(Long matchId, MatchUpdateRequest request) {
        rejectUnknownFields(request);
        Long teamId = CurrentUser.principal().teamId();
        // 行锁：与报名域名额锁串行化，并保证后续版本/容量判断读到最新已提交数据。
        TeamMatch match = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        if (notEditable(match.getStatus())) {
            throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "当前状态不可编辑比赛");
        }
        if (!request.getVersion().equals(match.getVersion())) {
            throw versionConflict(match.getVersion());
        }

        Map<String, Object> before = MatchAuditSnapshot.of(match);
        applyChanges(match, request);

        int updated = teamMatchMapper.update(null, new LambdaUpdateWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId)
                .eq(TeamMatch::getVersion, request.getVersion())
                .notIn(TeamMatch::getStatus,
                        MatchStatus.FINISHED, MatchStatus.POSTPONED, MatchStatus.CANCELLED)
                .set(TeamMatch::getTitle, match.getTitle())
                .set(TeamMatch::getStartAt, match.getStartAt())
                .set(TeamMatch::getEndAt, match.getEndAt())
                .set(TeamMatch::getSignupDeadline, match.getSignupDeadline())
                .set(TeamMatch::getCancelLock, match.getCancelLock())
                .set(TeamMatch::getVenue, match.getVenue())
                .set(TeamMatch::getCompetition, match.getCompetition())
                .set(TeamMatch::getRound, match.getRound())
                .set(TeamMatch::getHomeTeamName, match.getHomeTeamName())
                .set(TeamMatch::getAwayTeamName, match.getAwayTeamName())
                .set(TeamMatch::getCapacity, match.getCapacity())
                .set(TeamMatch::getGuestCapacity, match.getGuestCapacity())
                .set(TeamMatch::getWaitlistCapacity, match.getWaitlistCapacity())
                .set(TeamMatch::getIncludeGuests, match.getIncludeGuests())
                .set(TeamMatch::getVersion, match.getVersion() + 1));
        if (updated == 0) {
            TeamMatch current = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
            }
            if (notEditable(current.getStatus())) {
                throw new BusinessException(ErrorCode.MATCH_STATE_CONFLICT, "当前状态不可编辑比赛");
            }
            throw versionConflict(current.getVersion());
        }

        TeamMatch saved = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        auditRecorder.recordCurrent("MATCH_UPDATE", "MATCH", matchId, before, MatchAuditSnapshot.of(saved));
        return toView(saved, counts(matchId));
    }

    @Transactional
    public MatchAdminView changeStatus(Long matchId, MatchStatusRequest request) {
        return doChangeStatus(matchId, request);
    }

    private MatchAdminView doChangeStatus(Long matchId, MatchStatusRequest request) {
        MatchStatus target = MatchStatus.of(request.status());
        MatchStatusMachine.requireManualTarget(target);
        Long teamId = CurrentUser.principal().teamId();
        Long userId = CurrentUser.principal().userId();
        TeamMatch match = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        if (!request.version().equals(match.getVersion())) {
            throw versionConflict(match.getVersion());
        }

        Map<String, Object> before = MatchAuditSnapshot.of(match);
        // 未显式恢复自动流转时，手动修改默认停止自动流转。
        boolean manualStatus = request.manualStatus() == null || request.manualStatus();
        int updated = teamMatchMapper.update(null, new LambdaUpdateWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId)
                .eq(TeamMatch::getVersion, request.version())
                .set(TeamMatch::getStatus, target)
                .set(TeamMatch::getManualStatus, manualStatus)
                .set(TeamMatch::getStatusUpdatedAt, Instant.now(clock))
                .set(TeamMatch::getStatusUpdatedBy, userId)
                .set(TeamMatch::getVersion, match.getVersion() + 1));
        if (updated == 0) {
            TeamMatch current = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
            if (current == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
            }
            throw versionConflict(current.getVersion());
        }

        TeamMatch saved = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        auditRecorder.recordCurrent("MATCH_STATUS_CHANGE", "MATCH", matchId, before, MatchAuditSnapshot.of(saved));
        return toView(saved, counts(matchId));
    }

    /**
     * C 端详情页状态入口（MAT-12）：普通状态同 MAT-06；延期/取消分别复用 MAT-07/MAT-08。
     */
    @Transactional
    public MatchAdminView changeStatusFromPublic(Long matchId, MatchStatusRequest request) {
        MatchStatus target = MatchStatus.of(request.status());
        if (target == MatchStatus.POSTPONED) {
            if (!CurrentUser.principal().permissions().contains(MATCH_EDIT_PERMISSION)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            Map<String, String> details = new LinkedHashMap<>();
            if (request.startAt() == null) {
                details.put("startAt", "不能为空");
            }
            if (request.endAt() == null) {
                details.put("endAt", "不能为空");
            }
            if (!details.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "延期必须提供新开始/结束时间", Map.of("details", details));
            }
            return doPostpone(matchId,
                    new MatchScheduleRequest(request.startAt(), request.endAt(), request.version()));
        }
        if (target == MatchStatus.CANCELLED) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "取消必须填写原因", Map.of("details", Map.of("reason", "不能为空")));
            }
            return doCancel(matchId, new MatchCancelRequest(request.reason(), request.version()));
        }
        return doChangeStatus(matchId, request);
    }

    @Transactional
    public MatchAdminView postpone(Long matchId, MatchScheduleRequest request) {
        return doPostpone(matchId, request);
    }

    private MatchAdminView doPostpone(Long matchId, MatchScheduleRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        TeamMatch match = lockForTransition(matchId, teamId, request.version(),
                MatchStatusMachine::requirePostponable);
        MatchTimeRules.Schedule schedule = MatchTimeRules.validateAndDerive(
                request.startAt(), request.endAt());

        Map<String, Object> before = MatchAuditSnapshot.of(match);
        int updated = teamMatchMapper.update(null, new LambdaUpdateWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId)
                .eq(TeamMatch::getVersion, request.version())
                .notIn(TeamMatch::getStatus,
                        MatchStatus.FINISHED, MatchStatus.POSTPONED, MatchStatus.CANCELLED)
                .set(TeamMatch::getStartAt, schedule.startAt())
                .set(TeamMatch::getEndAt, schedule.endAt())
                .set(TeamMatch::getSignupDeadline, schedule.signupDeadline())
                .set(TeamMatch::getCancelLock, schedule.cancelLock())
                .set(TeamMatch::getStatus, MatchStatus.POSTPONED)
                .set(TeamMatch::getManualStatus, true)
                .set(TeamMatch::getStatusUpdatedAt, Instant.now(clock))
                .set(TeamMatch::getStatusUpdatedBy, CurrentUser.principal().userId())
                .set(TeamMatch::getVersion, match.getVersion() + 1));
        TeamMatch saved = reloadAfterTransition(updated, matchId, teamId,
                current -> MatchStatusMachine.requirePostponable(current.getStatus()));
        auditRecorder.recordCurrent("MATCH_POSTPONE", "MATCH", matchId, before, MatchAuditSnapshot.of(saved));
        return toView(saved, counts(matchId));
    }

    @Transactional
    public MatchAdminView reschedule(Long matchId, MatchScheduleRequest request) {
        return doReschedule(matchId, request);
    }

    private MatchAdminView doReschedule(Long matchId, MatchScheduleRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        TeamMatch match = lockForTransition(matchId, teamId, request.version(),
                MatchStatusMachine::requireReschedulable);
        MatchTimeRules.Schedule schedule = MatchTimeRules.validateAndDerive(
                request.startAt(), request.endAt());

        Map<String, Object> before = MatchAuditSnapshot.of(match);
        int updated = teamMatchMapper.update(null, new LambdaUpdateWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId)
                .eq(TeamMatch::getVersion, request.version())
                .eq(TeamMatch::getStatus, MatchStatus.POSTPONED)
                .set(TeamMatch::getStartAt, schedule.startAt())
                .set(TeamMatch::getEndAt, schedule.endAt())
                .set(TeamMatch::getSignupDeadline, schedule.signupDeadline())
                .set(TeamMatch::getCancelLock, schedule.cancelLock())
                .set(TeamMatch::getStatus, MatchStatus.SIGNUP_OPEN)
                .set(TeamMatch::getManualStatus, false)
                .set(TeamMatch::getStatusUpdatedAt, Instant.now(clock))
                .set(TeamMatch::getStatusUpdatedBy, CurrentUser.principal().userId())
                .set(TeamMatch::getVersion, match.getVersion() + 1));
        TeamMatch saved = reloadAfterTransition(updated, matchId, teamId,
                current -> MatchStatusMachine.requireReschedulable(current.getStatus()));
        auditRecorder.recordCurrent("MATCH_RESCHEDULE", "MATCH", matchId, before, MatchAuditSnapshot.of(saved));
        return toView(saved, counts(matchId));
    }

    @Transactional
    public MatchAdminView cancel(Long matchId, MatchCancelRequest request) {
        return doCancel(matchId, request);
    }

    private MatchAdminView doCancel(Long matchId, MatchCancelRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        TeamMatch match = lockForTransition(matchId, teamId, request.version(),
                MatchStatusMachine::requireCancellable);

        Map<String, Object> before = MatchAuditSnapshot.of(match);
        int updated = teamMatchMapper.update(null, new LambdaUpdateWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamId)
                .eq(TeamMatch::getVersion, request.version())
                .notIn(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED)
                .set(TeamMatch::getStatus, MatchStatus.CANCELLED)
                .set(TeamMatch::getManualStatus, true)
                .set(TeamMatch::getStatusUpdatedAt, Instant.now(clock))
                .set(TeamMatch::getStatusUpdatedBy, CurrentUser.principal().userId())
                .set(TeamMatch::getVersion, match.getVersion() + 1));
        TeamMatch saved = reloadAfterTransition(updated, matchId, teamId,
                current -> MatchStatusMachine.requireCancellable(current.getStatus()));
        Map<String, Object> after = MatchAuditSnapshot.of(saved);
        after.put("reason", request.reason().strip());
        auditRecorder.recordCurrent("MATCH_CANCEL", "MATCH", matchId, before, after);
        return toView(saved, counts(matchId));
    }

    private TeamMatch lockForTransition(Long matchId, Long teamId, Integer version,
                                        Consumer<MatchStatus> stateGuard) {
        TeamMatch match = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        stateGuard.accept(match.getStatus());
        if (!version.equals(match.getVersion())) {
            throw versionConflict(match.getVersion());
        }
        return match;
    }

    private TeamMatch reloadAfterTransition(int updated, Long matchId, Long teamId,
                                            Consumer<TeamMatch> stateGuard) {
        TeamMatch current = teamMatchMapper.selectByIdForUpdate(matchId, teamId);
        if (current == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        if (updated == 0) {
            stateGuard.accept(current);
            throw versionConflict(current.getVersion());
        }
        return current;
    }

    private void rejectUnknownFields(MatchUpdateRequest request) {
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

    private boolean notEditable(MatchStatus status) {
        return status == MatchStatus.FINISHED
                || status == MatchStatus.CANCELLED
                || status == MatchStatus.POSTPONED;
    }

    private void applyChanges(TeamMatch match, MatchUpdateRequest request) {
        if (request.getTitle() != null) {
            match.setTitle(requiredText(request.getTitle(), "title", "比赛名称"));
        }
        if (request.getVenue() != null) {
            match.setVenue(requiredText(request.getVenue(), "venue", "场地"));
        }
        if (request.getHomeTeamName() != null) {
            match.setHomeTeamName(requiredText(request.getHomeTeamName(), "homeTeamName", "主队名称"));
        }
        if (request.getAwayTeamName() != null) {
            match.setAwayTeamName(requiredText(request.getAwayTeamName(), "awayTeamName", "客队名称"));
        }
        if (request.hasCompetition()) {
            match.setCompetition(MatchQueryParsing.normalize(request.getCompetition()));
        }
        if (request.hasRound()) {
            match.setRound(request.getRound());
        }
        if (request.getIncludeGuests() != null) {
            match.setIncludeGuests(request.getIncludeGuests());
        }
        if (request.getWaitlistCapacity() != null) {
            match.setWaitlistCapacity(request.getWaitlistCapacity());
        }
        if (request.getStartAt() != null || request.getEndAt() != null) {
            MatchTimeRules.Schedule schedule = MatchTimeRules.validateAndDerive(
                    request.getStartAt() == null ? match.getStartAt() : request.getStartAt(),
                    request.getEndAt() == null ? match.getEndAt() : request.getEndAt());
            match.setStartAt(schedule.startAt());
            match.setEndAt(schedule.endAt());
            match.setSignupDeadline(schedule.signupDeadline());
            match.setCancelLock(schedule.cancelLock());
        }
        if (request.getCapacity() != null || request.getGuestCapacity() != null) {
            RegistrationQueueCounts counts = counts(match.getId());
            if (request.getCapacity() != null) {
                if (request.getCapacity() < counts.confirmedCount()) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "正式名额不得低于已确认正式人数");
                }
                match.setCapacity(request.getCapacity());
            }
            if (request.getGuestCapacity() != null) {
                if (request.getGuestCapacity() < counts.guestCount()) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "外挂名额不得低于已确认外挂人数");
                }
                match.setGuestCapacity(request.getGuestCapacity());
            }
        }
    }

    private RegistrationQueueCounts counts(Long matchId) {
        return registrationReadPort.queueCounts(List.of(matchId))
                .getOrDefault(matchId, RegistrationQueueCounts.EMPTY);
    }

    private String requiredText(String value, String field, String label) {
        String normalized = MatchQueryParsing.normalize(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, label + "不能为空",
                    Map.of("details", Map.of(field, "不能为空")));
        }
        return normalized;
    }

    private BusinessException versionConflict(int currentVersion) {
        return new BusinessException(ErrorCode.VERSION_CONFLICT, "比赛已被他人修改，请刷新后重试",
                Map.of("currentVersion", currentVersion));
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(5, 30));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "场次编号冲突，请重试");
        }
    }

    private TeamMatch prepare(Long teamId, MatchCreateRequest request) {
        if (request.type() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛类型不能为空");
        }
        MatchType type = MatchQueryParsing.type(request.type());
        String title = MatchQueryParsing.normalize(request.title());
        String homeTeamName = MatchQueryParsing.normalize(request.homeTeamName());
        String awayTeamName = MatchQueryParsing.normalize(request.awayTeamName());
        String venue = MatchQueryParsing.normalize(request.venue());
        String format = defaultWhenBlank(request.format(), DEFAULT_FORMAT);
        String competition = MatchQueryParsing.normalize(request.competition());
        String season = season(request.season());
        MatchTimeRules.Schedule schedule = MatchTimeRules.validateAndDerive(
                request.startAt(), request.endAt());

        OurSide ourSide = ourSide(type, request.ourSide(), homeTeamName, awayTeamName);

        if ((request.scoreHome() == null) != (request.scoreAway() == null)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "比分必须成对传入");
        }
        int scoreHome = request.scoreHome() == null ? 0 : request.scoreHome();
        int scoreAway = request.scoreAway() == null ? 0 : request.scoreAway();

        RegistrationDefaults defaults = null;
        if (request.capacity() == null || request.guestCapacity() == null
                || request.waitlistCapacity() == null) {
            defaults = registrationDefaultsPort.load(teamId);
        }
        int capacity = request.capacity() == null ? defaults.maxPlayers() : request.capacity();
        int guestCapacity = request.guestCapacity() == null ? defaults.maxGuests() : request.guestCapacity();
        int waitlistCapacity = request.waitlistCapacity() == null
                ? defaults.maxWaitlist() : request.waitlistCapacity();
        if (capacity < 1 || guestCapacity < 0 || waitlistCapacity < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "名额配置不合法");
        }

        TeamMatch match = new TeamMatch();
        match.setTeamId(teamId);
        match.setTitle(title);
        match.setType(type);
        match.setFormat(format);
        match.setStartAt(schedule.startAt());
        match.setEndAt(schedule.endAt());
        match.setSignupDeadline(schedule.signupDeadline());
        match.setCancelLock(schedule.cancelLock());
        match.setVenue(venue);
        match.setCompetition(competition);
        match.setSeason(season);
        match.setRound(request.round());
        match.setHomeTeamName(homeTeamName);
        match.setAwayTeamName(awayTeamName);
        match.setOurSide(ourSide);
        match.setStatus(MatchStatus.SIGNUP_OPEN);
        match.setManualStatus(false);
        match.setCapacity(capacity);
        match.setGuestCapacity(guestCapacity);
        match.setWaitlistCapacity(waitlistCapacity);
        match.setIncludeGuests(request.includeGuests() == null || request.includeGuests());
        match.setScoreHome(scoreHome);
        match.setScoreAway(scoreAway);
        match.setVersion(0);
        match.setCreatedBy(CurrentUser.principal().userId());
        return match;
    }

    private MatchAdminView persist(Long teamId, TeamMatch match) {
        match.setId(null);
        match.setSeasonMatchNo((int) (teamMatchMapper.maxSeasonMatchNo(teamId, match.getSeason()) + 1));
        match.setTotalMatchNo((int) (teamMatchMapper.maxTotalMatchNo(teamId) + 1));
        teamMatchMapper.insert(match);
        TeamMatch saved = teamMatchMapper.selectById(match.getId());
        auditRecorder.recordCurrent("MATCH_CREATE", "MATCH", saved.getId(), null, MatchAuditSnapshot.of(saved));
        return toView(saved, RegistrationQueueCounts.EMPTY);
    }

    private OurSide ourSide(MatchType type, Integer requested, String homeTeamName, String awayTeamName) {
        if (type != MatchType.OFFICIAL && type != MatchType.FRIENDLY) {
            return null;
        }
        if (requested != null) {
            return OurSide.of(requested);
        }
        if (homeTeamName.contains(OUR_TEAM_MARKER)) {
            return OurSide.HOME;
        }
        if (awayTeamName.contains(OUR_TEAM_MARKER)) {
            return OurSide.AWAY;
        }
        throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "对外比赛必须确定我方主客");
    }

    private String season(String requested) {
        String season = MatchQueryParsing.normalize(requested);
        if (season == null) {
            return String.valueOf(LocalDate.now(clock.withZone(MatchTimeRules.ZONE)).getYear());
        }
        return MatchQueryParsing.validSeason(season);
    }

    private String defaultWhenBlank(String value, String fallback) {
        String normalized = MatchQueryParsing.normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private MatchAdminView toView(TeamMatch match, RegistrationQueueCounts counts) {
        return new MatchAdminView(
                match.getId(),
                match.getTitle(),
                match.getType().code(),
                match.getType().label(),
                match.getFormat(),
                match.getStartAt(),
                match.getEndAt(),
                match.getSignupDeadline(),
                match.getCancelLock(),
                match.getVenue(),
                match.getCompetition(),
                match.getSeason(),
                match.getRound(),
                match.getHomeTeamName(),
                match.getAwayTeamName(),
                match.getOurSide() == null ? null : match.getOurSide().code(),
                match.getOurSide() == null ? null : match.getOurSide().label(),
                match.getStatus().code(),
                match.getStatus().label(),
                Boolean.TRUE.equals(match.getManualStatus()),
                match.getCapacity(),
                match.getGuestCapacity(),
                match.getWaitlistCapacity(),
                Boolean.TRUE.equals(match.getIncludeGuests()),
                match.getScoreHome(),
                match.getScoreAway(),
                match.getSeasonMatchNo(),
                match.getTotalMatchNo(),
                counts.confirmedCount(),
                counts.guestCount(),
                counts.waitlistCount(),
                match.getVersion(),
                match.getStatusUpdatedAt(),
                match.getStatusUpdatedBy(),
                match.getCreatedBy(),
                match.getCreatedAt(),
                match.getUpdatedAt());
    }
}
