package com.yunhefc.query.application;

import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchSummaryReadPort;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.registration.RegistrationQueueCounts;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.query.dto.MyRegistrationDtos.MatchSummary;
import com.yunhefc.query.dto.MyRegistrationDtos.MyMatchRegistrationView;
import com.yunhefc.query.dto.MyRegistrationDtos.MyRegistration;
import com.yunhefc.query.dto.MyRegistrationDtos.RegistrationView;
import com.yunhefc.query.dto.RegistrationDtos.RegistrationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * “我的报名”读模型：组合报名域的最新记录与比赛域摘要，供个人中心与比赛详情使用。
 */
@Service
@RequiredArgsConstructor
public class MyRegistrationsApplicationService {

    private final RegistrationReadPort registrationReadPort;
    private final MatchSummaryReadPort matchSummaryReadPort;
    private final PlayerReadPort playerReadPort;

    @Transactional(readOnly = true)
    public PageResponse<MyRegistration> list(String scope, long page, long pageSize) {
        Scope requested = Scope.parse(scope);
        PageQuery.of(page, pageSize);
        AuthPrincipal principal = CurrentUser.principal();

        PlayerSnapshot player = playerReadPort.findByUserId(principal.userId()).orElse(null);
        if (player == null) {
            return PageResponse.of(List.of(), 0, page, pageSize);
        }

        List<RegistrationSnapshot> latest = registrationReadPort.findLatestByPlayer(player.id());
        List<Long> matchIds = latest.stream().map(RegistrationSnapshot::matchId).toList();
        Map<Long, MatchSummarySnapshot> matches =
                matchSummaryReadPort.findSummaries(matchIds, principal.teamId());
        Map<Long, RegistrationQueueCounts> counts = registrationReadPort.queueCounts(matchIds);

        List<MyRegistration> items = new ArrayList<>();
        for (RegistrationSnapshot registration : latest) {
            MatchSummarySnapshot match = matches.get(registration.matchId());
            if (match == null) {
                continue;
            }
            boolean active = !match.finishedOrCancelled() && registration.active();
            if (active != requested.active()) {
                continue;
            }
            items.add(new MyRegistration(
                    RegistrationRow.from(registration, player),
                    toMatchSummary(match,
                            counts.getOrDefault(match.id(), RegistrationQueueCounts.EMPTY))));
        }
        return pageOf(items, page, pageSize);
    }

    @Transactional(readOnly = true)
    public MyMatchRegistrationView mineForMatch(Long matchId) {
        AuthPrincipal principal = CurrentUser.principal();
        matchSummaryReadPort.findSummary(matchId, principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));

        RegistrationView registration = registrationReadPort
                .findActiveByUser(matchId, principal.userId())
                .map(found -> new RegistrationView(
                        found.id(),
                        found.playerId(),
                        found.type(),
                        found.typeLabel(),
                        found.status(),
                        found.statusLabel(),
                        found.registeredAt(),
                        found.cancelledAt(),
                        found.active()))
                .orElse(null);
        return new MyMatchRegistrationView(registration, null);
    }

    /**
     * active/history 依赖比赛状态，需跨域组合后再分页，故在内存中切片；单人报名量有界。
     */
    private PageResponse<MyRegistration> pageOf(List<MyRegistration> items, long page, long pageSize) {
        long offset;
        try {
            offset = Math.multiplyExact(page - 1, pageSize);
        } catch (ArithmeticException ex) {
            return PageResponse.of(List.of(), items.size(), page, pageSize);
        }
        int from = (int) Math.min(offset, items.size());
        int to = (int) Math.min((long) from + pageSize, items.size());
        return PageResponse.of(new ArrayList<>(items.subList(from, to)), items.size(), page, pageSize);
    }

    private MatchSummary toMatchSummary(MatchSummarySnapshot match, RegistrationQueueCounts counts) {
        return new MatchSummary(
                match.id(),
                match.title(),
                match.type(),
                match.typeLabel(),
                match.startAt(),
                match.endAt(),
                match.venue(),
                match.round(),
                match.season(),
                match.homeTeamName(),
                match.awayTeamName(),
                match.ourSide(),
                match.ourSideLabel(),
                match.scoreHome(),
                match.scoreAway(),
                match.resultText(),
                match.status(),
                match.statusLabel(),
                match.totalMatchNo(),
                match.seasonMatchNo(),
                counts.confirmedCount(),
                counts.guestCount(),
                counts.waitlistCount());
    }

    private enum Scope {

        ACTIVE(true),
        HISTORY(false);

        private final boolean active;

        Scope(boolean active) {
            this.active = active;
        }

        boolean active() {
            return active;
        }

        static Scope parse(String requested) {
            String value = requested == null ? null : requested.strip();
            if (value == null || value.isBlank() || value.equals("active")) {
                return ACTIVE;
            }
            if (value.equals("history")) {
                return HISTORY;
            }
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "scope 必须为 active 或 history");
        }
    }
}
