package com.yunhefc.match.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.event.DerivedScoreSnapshot;
import com.yunhefc.contracts.event.EventScoreReadPort;
import com.yunhefc.contracts.registration.RegistrationQueueCounts;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.match.domain.MatchStatus;
import com.yunhefc.match.domain.MatchTimeRules;
import com.yunhefc.match.domain.MatchType;
import com.yunhefc.match.domain.TeamMatch;
import com.yunhefc.match.dto.MatchDtos.DerivedScore;
import com.yunhefc.match.dto.MatchDtos.MatchDetail;
import com.yunhefc.match.dto.MatchDtos.MatchPublicQuery;
import com.yunhefc.match.dto.MatchDtos.MatchSummary;
import com.yunhefc.common.time.ShanghaiTime;
import com.yunhefc.match.dto.MatchDtos.RegistrationView;
import com.yunhefc.match.infrastructure.TeamMatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final EventScoreReadPort eventScoreReadPort;

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
        MatchQueryParsing.DateRange dateRange =
                MatchQueryParsing.dateRange(query.startDate(), query.endDate());

        LambdaQueryWrapper<TeamMatch> wrapper = new LambdaQueryWrapper<TeamMatch>()
                .eq(TeamMatch::getTeamId, teamReadPort.currentTeamId())
                .eq(status != null, TeamMatch::getStatus, status)
                .eq(type != null, TeamMatch::getType, type)
                .eq(season != null, TeamMatch::getSeason, season)
                .like(venue != null, TeamMatch::getVenue, venue);
        if (MatchQueryParsing.SCOPE_UPCOMING.equals(scope)) {
            // 状态机是主口径；再加时间下限，避免手工冻结或自动流转滞后的过期比赛占用待进行列表。
            wrapper.notIn(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED)
                    .gt(TeamMatch::getEndAt, ShanghaiTime.now());
        } else if (MatchQueryParsing.SCOPE_HISTORY.equals(scope)) {
            wrapper.in(TeamMatch::getStatus, MatchStatus.FINISHED, MatchStatus.CANCELLED);
        }
        if (month != null) {
            wrapper.ge(TeamMatch::getStartAt, monthStart(month))
                    .lt(TeamMatch::getStartAt, monthStart(month.plusMonths(1)));
        }
        if (dateRange != null) {
            wrapper.ge(TeamMatch::getStartAt, dateRange.startAt())
                    .lt(TeamMatch::getStartAt, dateRange.endExclusiveAt());
        }
        if (ascending) {
            wrapper.orderByAsc(TeamMatch::getStartAt);
        } else {
            wrapper.orderByDesc(TeamMatch::getStartAt);
        }
        wrapper.orderByAsc(TeamMatch::getId);

        Page<TeamMatch> result = teamMatchMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<Long> matchIds = result.getRecords().stream().map(TeamMatch::getId).toList();
        Map<Long, RegistrationQueueCounts> counts = registrationReadPort.queueCounts(matchIds);
        Map<Long, DerivedScoreSnapshot> derivedScores = eventScoreReadPort.findDerivedScores(matchIds);
        List<MatchSummary> items = result.getRecords().stream()
                .map(match -> toSummary(match,
                        counts.getOrDefault(match.getId(), RegistrationQueueCounts.EMPTY),
                        derivedScores.get(match.getId())))
                .toList();
        return PageResponse.of(items, result.getTotal(), page, pageSize);
    }

    /**
     * C 端赛季筛选：本队有效比赛赛季 ∪ 当前年份，倒序。
     */
    @Transactional(readOnly = true)
    public List<String> seasons() {
        Set<String> seasons = new HashSet<>(
                teamMatchMapper.selectEffectiveSeasons(teamReadPort.currentTeamId()));
        seasons.add(String.valueOf(LocalDate.now(ShanghaiTime.ZONE).getYear()));
        return seasons.stream()
                .filter(season -> season.matches("\\d{4}"))
                .sorted(Comparator.reverseOrder())
                .toList();
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
        DerivedScoreSnapshot derivedScore = eventScoreReadPort.findDerivedScore(matchId).orElse(null);
        return toDetail(match, counts, derivedScore, myRegistration(matchId));
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

    private MatchSummary toSummary(TeamMatch match, RegistrationQueueCounts counts,
                                   DerivedScoreSnapshot derived) {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(match, derived);
        return new MatchSummary(
                summary.id(),
                summary.title(),
                summary.type(),
                summary.typeLabel(),
                summary.startAt(),
                summary.endAt(),
                summary.venue(),
                summary.round(),
                summary.season(),
                summary.homeTeamName(),
                summary.awayTeamName(),
                summary.ourSide(),
                summary.ourSideLabel(),
                summary.scoreHome(),
                summary.scoreAway(),
                summary.resultText(),
                summary.status(),
                summary.statusLabel(),
                summary.totalMatchNo(),
                summary.seasonMatchNo(),
                counts.confirmedCount(),
                counts.guestCount(),
                counts.waitlistCount());
    }

    private static DerivedScore toDerivedScore(DerivedScoreSnapshot score) {
        return score == null ? null : new DerivedScore(score.scoreHome(), score.scoreAway());
    }

    private MatchDetail toDetail(TeamMatch match, RegistrationQueueCounts counts,
                                 DerivedScoreSnapshot derived, RegistrationView mine) {
        MatchSummarySnapshot summary = MatchSummaryMapping.toSummary(match, derived);
        return new MatchDetail(
                summary.id(),
                summary.title(),
                summary.type(),
                summary.typeLabel(),
                summary.startAt(),
                summary.endAt(),
                summary.venue(),
                summary.round(),
                summary.season(),
                summary.homeTeamName(),
                summary.awayTeamName(),
                summary.ourSide(),
                summary.ourSideLabel(),
                summary.scoreHome(),
                summary.scoreAway(),
                summary.resultText(),
                summary.status(),
                summary.statusLabel(),
                summary.totalMatchNo(),
                summary.seasonMatchNo(),
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
                toDerivedScore(derived),
                mine);
    }

    private LocalDateTime monthStart(YearMonth month) {
        return month.atDay(1).atStartOfDay();
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
