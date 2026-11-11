package com.yunhefc.query.application;

import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.match.MatchSummaryReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.registration.RegistrationReadPort;
import com.yunhefc.contracts.registration.RegistrationRosterPage;
import com.yunhefc.contracts.registration.RegistrationSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.query.dto.RegistrationDtos.RegistrationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 比赛报名名单读模型：报名域分页与球员域档案的跨域只读组合。
 */
@Service
@RequiredArgsConstructor
public class RegistrationRosterApplicationService {

    private final MatchSummaryReadPort matchSummaryReadPort;
    private final RegistrationReadPort registrationReadPort;
    private final PlayerReadPort playerReadPort;

    @Transactional(readOnly = true)
    public PageResponse<RegistrationRow> list(Long matchId, Integer type, Integer status,
                                              long page, long pageSize) {
        PageQuery.of(page, pageSize);
        AuthPrincipal principal = CurrentUser.principal();

        matchSummaryReadPort.findSummary(matchId, principal.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "比赛不存在"));

        RegistrationRosterPage roster =
                registrationReadPort.findRoster(matchId, type, status, page, pageSize);
        Map<Long, PlayerSnapshot> players = playerReadPort.findByIds(
                        principal.teamId(),
                        roster.items().stream().map(RegistrationSnapshot::playerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity()));

        // 球员档案缺失（如已注销或被伪删除）时无法渲染该行，跳过而不是返回 500。
        List<RegistrationRow> items = roster.items().stream()
                .filter(registration -> players.containsKey(registration.playerId()))
                .map(registration -> RegistrationRow.from(registration,
                        players.get(registration.playerId())))
                .toList();
        return PageResponse.of(items, roster.total(), page, pageSize);
    }
}
