package com.yunhefc.grouping.domain;

import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.registration.ConfirmedRegistration;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GroupingCandidatesTest {

    @Test
    void excludesGuestsWhenMatchDoesNotIncludeThem() {
        List<GroupingCandidate> candidates = GroupingCandidates.select(false,
                List.of(new ConfirmedRegistration(1L, PlayerType.PLAYER.code()),
                        new ConfirmedRegistration(2L, PlayerType.GUEST.code()),
                        new ConfirmedRegistration(3L, PlayerType.PLAYER.code())),
                List.of(player(1L, 11L, PlayerType.PLAYER), player(2L, 12L, PlayerType.PLAYER),
                        player(3L, 13L, PlayerType.GUEST)),
                accounts(11L, 12L, 13L));

        assertThat(candidates).extracting(GroupingCandidate::playerId).containsExactly(1L);
    }

    @Test
    void includesGuestsWhenMatchAllowsThem() {
        List<GroupingCandidate> candidates = GroupingCandidates.select(true,
                List.of(new ConfirmedRegistration(1L, PlayerType.GUEST.code())),
                List.of(player(1L, 11L, PlayerType.GUEST)),
                accounts(11L));

        assertThat(candidates).extracting(GroupingCandidate::playerId).containsExactly(1L);
    }

    @Test
    void excludesMissingOrInactiveAccounts() {
        List<GroupingCandidate> candidates = GroupingCandidates.select(true,
                List.of(new ConfirmedRegistration(1L, PlayerType.PLAYER.code()),
                        new ConfirmedRegistration(2L, PlayerType.PLAYER.code())),
                List.of(player(1L, 11L, PlayerType.PLAYER), player(2L, 12L, PlayerType.PLAYER)),
                Map.of(11L, account(11L, 0), 12L, account(12L, 1)));

        assertThat(candidates).extracting(GroupingCandidate::playerId).containsExactly(2L);
    }

    @Test
    void deduplicatesAndSortsByPlayerId() {
        List<GroupingCandidate> candidates = GroupingCandidates.select(true,
                List.of(new ConfirmedRegistration(5L, PlayerType.PLAYER.code()),
                        new ConfirmedRegistration(3L, PlayerType.PLAYER.code()),
                        new ConfirmedRegistration(5L, PlayerType.PLAYER.code())),
                List.of(player(5L, 15L, PlayerType.PLAYER), player(3L, 13L, PlayerType.PLAYER)),
                accounts(13L, 15L));

        assertThat(candidates).extracting(GroupingCandidate::playerId).containsExactly(3L, 5L);
    }

    private static PlayerSnapshot player(Long id, Long userId, PlayerType type) {
        return new PlayerSnapshot(id, 1L, userId, "球员" + id, id.intValue(), type.code(), 1, 3);
    }

    private static Map<Long, UserAccountSnapshot> accounts(Long... userIds) {
        Map<Long, UserAccountSnapshot> accounts = new java.util.HashMap<>();
        for (Long userId : userIds) {
            accounts.put(userId, account(userId, 1));
        }
        return accounts;
    }

    private static UserAccountSnapshot account(Long userId, int status) {
        return new UserAccountSnapshot(userId, "139****0000", status, "ACTIVE",
                false, Set.of(), Instant.EPOCH);
    }
}
