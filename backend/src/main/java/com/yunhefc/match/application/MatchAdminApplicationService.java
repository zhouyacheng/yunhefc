package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.audit.application.AuditRecorder;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.settings.RegistrationDefaults;
import com.yunhefc.contracts.settings.RegistrationDefaultsPort;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchTimeRules;
import com.yunhefc.match.domain.MatchType;
import com.yunhefc.match.domain.OurSide;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.dto.MatchDtos.MatchAdminQuery;
import com.yunhefc.match.dto.MatchDtos.MatchAdminView;
import com.yunhefc.match.dto.MatchDtos.MatchCreateRequest;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchAdminApplicationService {

    private static final int CREATE_ATTEMPTS = 20;
    private static final String DEFAULT_FORMAT = "8人制";
    private static final String OUR_TEAM_MARKER = "云河FC";

    private final TeamMatchMapper teamMatchMapper;
    private final RegistrationDefaultsPort registrationDefaultsPort;
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
        MatchStatus status = matchStatus(query.status());
        MatchType type = matchType(query.type());
        String season = seasonFilter(query.season());
        String scope = scope(query.scope());
        boolean ascending = ascending(query.sort());

        LambdaQueryWrapper<TeamMatch> wrapper = new LambdaQueryWrapper<TeamMatch>()
                .eq(TeamMatch::getTeamId, teamId)
                .eq(status != null, TeamMatch::getStatus, status)
                .eq(type != null, TeamMatch::getType, type)
                .eq(season != null, TeamMatch::getSeason, season);
        if ("upcoming".equals(scope)) {
            wrapper.notIn(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        } else if ("history".equals(scope)) {
            wrapper.in(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        }
        if (ascending) {
            wrapper.orderByAsc(TeamMatch::getStartAt);
        } else {
            wrapper.orderByDesc(TeamMatch::getStartAt);
        }
        wrapper.orderByAsc(TeamMatch::getId);

        Page<TeamMatch> result = teamMatchMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<MatchAdminView> items = result.getRecords().stream().map(this::toView).toList();
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
        return toView(match);
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
        MatchType type = requiredMatchType(request.type());
        String title = normalize(request.title());
        String homeTeamName = normalize(request.homeTeamName());
        String awayTeamName = normalize(request.awayTeamName());
        String venue = normalize(request.venue());
        String format = defaultWhenBlank(request.format(), DEFAULT_FORMAT);
        String competition = normalize(request.competition());
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
        auditRecorder.recordCurrent("MATCH_CREATE", "MATCH", saved.getId(), null, snapshot(saved));
        return toView(saved);
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

    private MatchType requiredMatchType(Integer code) {
        if (code == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛类型不能为空");
        }
        return matchType(code);
    }

    private MatchType matchType(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return MatchType.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛类型不合法");
        }
    }

    private MatchStatus matchStatus(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return MatchStatus.of(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "比赛状态不合法");
        }
    }

    private String seasonFilter(String requested) {
        String season = normalize(requested);
        return season == null ? null : validSeason(season);
    }

    private String scope(String requested) {
        String scope = normalize(requested);
        if (scope == null) {
            return "all";
        }
        if (!scope.equals("all") && !scope.equals("upcoming") && !scope.equals("history")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "scope 参数不合法");
        }
        return scope;
    }

    private boolean ascending(String requested) {
        String sort = normalize(requested);
        if (sort == null || sort.equals("startAt:desc")) {
            return false;
        }
        if (sort.equals("startAt:asc")) {
            return true;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数不合法");
    }

    private String season(String requested) {
        String season = normalize(requested);
        if (season == null) {
            return String.valueOf(LocalDate.now(clock.withZone(MatchTimeRules.ZONE)).getYear());
        }
        return validSeason(season);
    }

    private String validSeason(String season) {
        if (!season.matches("\\d{4}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "赛季必须为四位年份");
        }
        return season;
    }

    private String defaultWhenBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isBlank() ? null : stripped;
    }

    private Map<String, Object> snapshot(TeamMatch match) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", match.getTitle());
        values.put("type", match.getType().code());
        values.put("format", match.getFormat());
        values.put("startAt", match.getStartAt());
        values.put("endAt", match.getEndAt());
        values.put("signupDeadline", match.getSignupDeadline());
        values.put("cancelLock", match.getCancelLock());
        values.put("venue", match.getVenue());
        values.put("competition", match.getCompetition());
        values.put("season", match.getSeason());
        values.put("round", match.getRound());
        values.put("homeTeamName", match.getHomeTeamName());
        values.put("awayTeamName", match.getAwayTeamName());
        values.put("ourSide", match.getOurSide() == null ? null : match.getOurSide().code());
        values.put("status", match.getStatus().code());
        values.put("manualStatus", match.getManualStatus());
        values.put("capacity", match.getCapacity());
        values.put("guestCapacity", match.getGuestCapacity());
        values.put("waitlistCapacity", match.getWaitlistCapacity());
        values.put("includeGuests", match.getIncludeGuests());
        values.put("scoreHome", match.getScoreHome());
        values.put("scoreAway", match.getScoreAway());
        values.put("seasonMatchNo", match.getSeasonMatchNo());
        values.put("totalMatchNo", match.getTotalMatchNo());
        return values;
    }

    private MatchAdminView toView(TeamMatch match) {
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
                match.getVersion(),
                match.getStatusUpdatedAt(),
                match.getStatusUpdatedBy(),
                match.getCreatedBy(),
                match.getCreatedAt(),
                match.getUpdatedAt());
    }
}
