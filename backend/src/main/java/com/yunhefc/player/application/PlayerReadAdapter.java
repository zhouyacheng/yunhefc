package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlayerReadAdapter implements PlayerReadPort {

    private final PlayerMapper playerMapper;

    @Override
    public Optional<PlayerSnapshot> findByUserId(Long userId) {
        return Optional.ofNullable(findEntityByUserId(playerMapper, userId)).map(PlayerReadAdapter::toSnapshot);
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
                player.getSkill());
    }
}
