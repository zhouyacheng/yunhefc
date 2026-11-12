package com.yunhefc.query.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.player.PlayerPublicSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.contracts.team.TeamReadPort;
import com.yunhefc.infra.storage.AvatarUrlSigner;
import com.yunhefc.query.dto.TeamPlayerDtos.TeamPlayerView;
import com.yunhefc.query.dto.TeamPlayerDtos.TeamPlayersView;
import com.yunhefc.stats.application.RosterStats;
import com.yunhefc.stats.application.StatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 球队页球员预览：球员域公开名单与统计汇总的跨域只读组合。
 */
@Service
@RequiredArgsConstructor
public class TeamPlayersApplicationService {

    private static final List<Integer> POSITION_ORDER = List.of(
            Position.FORWARD.code(),
            Position.MIDFIELDER.code(),
            Position.DEFENDER.code(),
            Position.GOALKEEPER.code());

    private final TeamReadPort teamReadPort;
    private final StatsQueryService statsQueryService;
    private final AvatarUrlSigner avatarUrlSigner;

    @Transactional(readOnly = true)
    public TeamPlayersView list(Integer limit, boolean all) {
        int perGroup = all ? Integer.MAX_VALUE : validateLimit(limit);
        Long teamId = teamReadPort.currentTeamId();
        String season = statsQueryService.currentSeason();
        List<RosterStats> roster = statsQueryService.rosterStats(teamId, season);

        Comparator<RosterStats> order = Comparator
                .comparing((RosterStats row) -> row.stats().appearances(), Comparator.reverseOrder())
                .thenComparing(row -> row.player().jersey(), Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(row -> row.player().id());

        List<TeamPlayerView> items = new ArrayList<>();
        for (int position : POSITION_ORDER) {
            roster.stream()
                    .filter(row -> row.player().position() == position)
                    .sorted(order)
                    .limit(perGroup)
                    .map(this::toView)
                    .forEach(items::add);
        }
        return new TeamPlayersView(items, roster.size());
    }

    private int validateLimit(Integer limit) {
        int value = limit == null ? 10 : limit;
        if (value < 1 || value > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "limit 必须为 1–100");
        }
        return value;
    }

    private TeamPlayerView toView(RosterStats row) {
        PlayerPublicSnapshot player = row.player();
        Position position = Position.of(player.position());
        return new TeamPlayerView(
                player.id(),
                player.name(),
                player.jersey(),
                position.code(),
                position.label(),
                player.secondaryPositions(),
                player.secondaryPositions().stream().map(code -> Position.of(code).label()).toList(),
                player.region(),
                avatarUrlSigner.sign(player.avatarUrl()),
                player.skill(),
                player.type(),
                PlayerType.of(player.type()).label(),
                row.stats().appearances());
    }
}
