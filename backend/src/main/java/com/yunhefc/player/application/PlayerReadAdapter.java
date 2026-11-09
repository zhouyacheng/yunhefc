package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PlayerReadAdapter implements PlayerReadPort {

    private final PlayerMapper playerMapper;

    public PlayerReadAdapter(PlayerMapper playerMapper) {
        this.playerMapper = playerMapper;
    }

    @Override
    public Optional<PlayerSnapshot> findByUserId(Long userId) {
        return Optional.ofNullable(findEntityByUserId(playerMapper, userId)).map(PlayerReadAdapter::toSnapshot);
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
                player.getType(),
                player.getPosition(),
                player.getSkill());
    }
}
