package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.contracts.player.PlayerPublicSnapshot;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.domain.SecondaryPositions;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PlayerReadAdapter implements PlayerReadPort {

    private static final int ACTIVE = 1;

    private final PlayerMapper playerMapper;
    private final UserAccountReadPort userAccountReadPort;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<PlayerSnapshot> findById(Long playerId) {
        return Optional.ofNullable(playerMapper.selectById(playerId)).map(PlayerReadAdapter::toSnapshot);
    }

    @Override
    public Optional<PlayerSnapshot> findByUserId(Long userId) {
        return Optional.ofNullable(findEntityByUserId(playerMapper, userId)).map(PlayerReadAdapter::toSnapshot);
    }

    @Override
    public List<PlayerSnapshot> findByIds(Long teamId, Collection<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return List.of();
        }
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                        .eq(Player::getTeamId, teamId)
                        .in(Player::getId, playerIds))
                .stream()
                .map(PlayerReadAdapter::toSnapshot)
                .toList();
    }

    @Override
    public List<PlayerSnapshot> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return playerMapper.selectList(new LambdaQueryWrapper<Player>()
                        .in(Player::getUserId, userIds))
                .stream()
                .map(PlayerReadAdapter::toSnapshot)
                .toList();
    }

    @Override
    public List<PlayerPublicSnapshot> findPublicRoster(Long teamId) {
        List<Player> players = playerMapper.selectList(new LambdaQueryWrapper<Player>()
                .eq(Player::getTeamId, teamId)
                .eq(Player::getType, PlayerType.PLAYER));
        if (players.isEmpty()) {
            return List.of();
        }
        Set<Long> activeUserIds = userAccountReadPort.findUserIds(ACTIVE, null);
        return players.stream()
                .filter(player -> activeUserIds.contains(player.getUserId()))
                .map(this::toPublicSnapshot)
                .toList();
    }

    static Player findEntityByUserId(PlayerMapper playerMapper, Long userId) {
        return playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUserId, userId)
                .last("LIMIT 1"));
    }

    static PlayerSnapshot toSnapshot(Player player) {
        return new PlayerSnapshot(
                player.getId(),
                player.getTeamId(),
                player.getUserId(),
                player.getName(),
                player.getJersey(),
                player.getType().code(),
                player.getPosition().code(),
                player.getSkill(),
                player.getAvatarUrl());
    }

    private PlayerPublicSnapshot toPublicSnapshot(Player player) {
        return new PlayerPublicSnapshot(
                player.getId(),
                player.getName(),
                player.getJersey(),
                player.getPosition().code(),
                SecondaryPositions.parse(objectMapper, player.getSecondaryPositions()),
                player.getRegion(),
                player.getAvatarUrl(),
                player.getSkill(),
                player.getType().code());
    }
}
