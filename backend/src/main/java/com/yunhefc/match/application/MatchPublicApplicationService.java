package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.registration.RegistrationQueueCounts;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchTimeRules;
import com.yunhefc.match.domain.MatchType;
import com.yunhefc.match.domain.OurSide;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.dto.MatchDtos.MatchDetail;
import com.yunhefc.match.dto.MatchDtos.MatchPublicQuery;
import com.yunhefc.match.dto.MatchDtos.MatchSummary;
import com.yunhefc.match.dto.MatchDtos.RegistrationView;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * C 端公开赛程与比赛详情；匿名可读，登录用户额外返回本人报名。
 */
@Service
@RequiredArgsConstructor
public class MatchPublicApplicationService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM");

    private final TeamMatchMapper teamMatchMapper;
    private final TeamReadPort teamReadPort;
    private final RegistrationReadPort registrationReadPort;

    @Transactional(readOnly = true)
    public PageResponse<MatchSummary> list(MatchPublicQuery query) {
        long page = query.page();
        long pageSize = query.pageSize();
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页参数不合法");
        }
        MatchStatus status = MatchQueryParsing.status(query.status());
        MatchType type = MatchQueryParsing.type(query.type());
        String season = MatchQueryParsing.seasonFilter(query.season());
        String venue = MatchQueryParsing.normalize(query.venue());
        String scope = MatchQueryParsing.scope(query.scope());
        boolean ascending = MatchQueryParsing.ascending(query.sort());
        YearMonth month = month(query.month());

        LambdaQueryWrapper<TeamMatch> wrapper = new LambdaQueryWrapper<TeamMatch>()
                .eq(TeamMatch::getTeamId, teamReadPort.currentTeamId())
                .eq(status != null, TeamMatch::getStatus, status)
                .eq(type != null, TeamMatch::getType, type)
                .eq(season != null, TeamMatch::getSeason, season)
                .like(venue != null, TeamMatch::getVenue, venue);
        if (MatchQueryParsing.SCOPE_UPCOMING.equals(scope)) {
            wrapper.notIn(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        } else if (MatchQueryParsing.SCOPE_HISTORY.equals(scope)) {
            wrapper.in(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        }
        if (month != null) {
            wrapper.ge(TeamMatch::getStartAt, monthStart(month))
                    .lt(TeamMatch::getStartAt, monthStart(month.plusMonths(1)));
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
        List<MatchSummary> items = result.getRecords().stream()
                .map(match -> toSummary(match, counts.getOrDefault(match.getId(), RegistrationQueueCounts.EMPTY)))
                .toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public MatchDetail detail(Long matchId) {
        TeamMatch match = teamMatchMapper.selectOne(new LambdaQueryWrapper<TeamMatch>()
                .eq(TeamMatch::getId, matchId)
                .eq(TeamMatch::getTeamId, teamReadPort.currentTeamId()));
        if (match == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在");
        }
        RegistrationQueueCounts counts = registrationReadPort.queueCounts(List.of(matchId))
                .getOrDefault(matchId, RegistrationQueueCounts.EMPTY);
        return toDetail(match, counts, myRegistration(matchId));
    }

    private RegistrationView myRegistration(Long matchId) {
        return CurrentUser.optionalPrincipal()
                .flatMap(principal -> registrationReadPort.findActiveByUser(matchId, principal.userId()))
                .map(MatchPublicApplicationService::toRegistrationView)
                .orElse(null);
    }

    private static RegistrationView toRegistrationView(RegistrationSnapshot registration) {
        return new RegistrationView(
                registration.id(),
                registration.playerId(),
                registration.type(),
                registration.typeLabel(),
                registration.status(),
                registration.statusLabel(),
                registration.registeredAt(),
                registration.cancelledAt(),
                registration.active());
    }

    private MatchSummary toSummary(TeamMatch match, RegistrationQueueCounts counts) {
        return new MatchSummary(
                match.getId(),
                match.getTitle(),
                match.getType().code(),
                match.getType().label(),
                match.getStartAt(),
                match.getEndAt(),
                match.getVenue(),
                match.getRound(),
                match.getSeason(),
                match.getHomeTeamName(),
                match.getAwayTeamName(),
                sideCode(match),
                sideLabel(match),
                match.getScoreHome(),
                match.getScoreAway(),
                resultText(match),
                match.getStatus().code(),
                match.getStatus().label(),
                match.getTotalMatchNo(),
                match.getSeasonMatchNo(),
                counts.confirmedCount(),
                counts.guestCount(),
                counts.waitlistCount());
    }

    private MatchDetail toDetail(TeamMatch match, RegistrationQueueCounts counts, RegistrationView mine) {
        return new MatchDetail(
                match.getId(),
                match.getTitle(),
                match.getType().code(),
                match.getType().label(),
                match.getStartAt(),
                match.getEndAt(),
                match.getVenue(),
                match.getRound(),
                match.getSeason(),
                match.getHomeTeamName(),
                match.getAwayTeamName(),
                sideCode(match),
                sideLabel(match),
                match.getScoreHome(),
                match.getScoreAway(),
                resultText(match),
                match.getStatus().code(),
                match.getStatus().label(),
                match.getTotalMatchNo(),
                match.getSeasonMatchNo(),
                counts.confirmedCount(),
                counts.guestCount(),
                counts.waitlistCount(),
                match.getFormat(),
                match.getCompetition(),
                match.getSignupDeadline(),
                match.getCancelLock(),
                match.getCapacity(),
                match.getGuestCapacity(),
                match.getWaitlistCapacity(),
                match.getVersion(),
                null,
                mine);
    }

    /**
     * 仅已结束比赛给出结果：对外按我方视角胜/负/平，队内/训练给出比分文本；无比分时为 null。
     */
    private String resultText(TeamMatch match) {
        if (match.getStatus() != MatchStatus.FINISHED) {
            return null;
        }
        Integer home = match.getScoreHome();
        Integer away = match.getScoreAway();
        if (home == null || away == null) {
            return null;
        }
        OurSide side = match.getOurSide();
        if (side == null) {
            return home + ":" + away;
        }
        int ours = side == OurSide.HOME ? home : away;
        int theirs = side == OurSide.HOME ? away : home;
        if (ours > theirs) {
            return "胜";
        }
        return ours < theirs ? "负" : "平";
    }

    private Integer sideCode(TeamMatch match) {
        return match.getOurSide() == null ? null : match.getOurSide().code();
    }

    private String sideLabel(TeamMatch match) {
        return match.getOurSide() == null ? null : match.getOurSide().label();
    }

    private Instant monthStart(YearMonth month) {
        return month.atDay(1).atStartOfDay(MatchTimeRules.ZONE).toInstant();
    }

    private YearMonth month(String requested) {
        String value = MatchQueryParsing.normalize(requested);
        if (value == null) {
            return null;
        }
        try {
            return YearMonth.parse(value, MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "月份必须为 YYYY-MM");
        }
    }
}
