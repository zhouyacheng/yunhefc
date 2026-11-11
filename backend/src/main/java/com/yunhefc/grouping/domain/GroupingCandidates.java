package com.yunhefc.grouping.domain;

import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.registration.ConfirmedRegistration;
import com.yunhefc.contracts.security.UserAccountSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分组候选：本场确认报名、本队球员、账号 ACTIVE，按比赛开关过滤外挂。
 */
public final class GroupingCandidates {

    private static final int ACTIVE_ACCOUNT = 1;

    private GroupingCandidates() {
    }

    public static List<GroupingCandidate> select(boolean includeGuests,
                                                 Collection<ConfirmedRegistration> registrations,
                                                 Collection<PlayerSnapshot> players,
                                                 Map<Long, UserAccountSnapshot> accounts) {
        Map<Long, Integer> registrationTypes = new LinkedHashMap<>();
        for (ConfirmedRegistration registration : registrations) {
            // 同一球员若存在多条确认报名，只要任一条为外挂即按外挂处理。
            registrationTypes.merge(registration.playerId(), registration.registrationType(), Math::min);
        }
        Map<Long, PlayerSnapshot> playersById = players.stream()
                .collect(Collectors.toMap(PlayerSnapshot::id, Function.identity(), (first, second) -> first));

        List<GroupingCandidate> candidates = new ArrayList<>();
        registrationTypes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    PlayerSnapshot player = playersById.get(entry.getKey());
                    if (player == null) {
                        return;
                    }
                    if (!includeGuests && (entry.getValue() == PlayerType.GUEST.code()
                            || player.type() == PlayerType.GUEST.code())) {
                        return;
                    }
                    UserAccountSnapshot account = accounts.get(player.userId());
                    if (account == null || account.status() == null
                            || account.status() != ACTIVE_ACCOUNT) {
                        return;
                    }
                    candidates.add(new GroupingCandidate(
                            player.id(), player.name(), player.jersey(), player.position(),
                            player.skill() == null ? 3 : player.skill()));
                });
        return candidates.stream()
                .sorted(Comparator.comparing(GroupingCandidate::playerId))
                .toList();
    }
}
