package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.player.PlayerType;
import com.yunhefc.contracts.player.Position;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 注册建档与转正编排中供 auth 调用的球员档案写入口。
 */
@Service
@RequiredArgsConstructor
public class PlayerProfileApplicationService {

    private final PlayerMapper playerMapper;

    public PlayerSnapshot createProfile(Long teamId, Long userId, String name, PlayerType type, Position position) {
        Player player = new Player();
        player.setTeamId(teamId);
        player.setUserId(userId);
        player.setName(name);
        player.setType(type);
        player.setPosition(position);
        player.setSecondaryPositions("[]");
        player.setSkill(3);
        playerMapper.insert(player);
        return PlayerReadAdapter.toSnapshot(player);
    }

    public void promoteToFormal(Long userId) {
        Player player = PlayerReadAdapter.findEntityByUserId(playerMapper, userId);
        if (player == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在");
        }
        if (player.getType() == PlayerType.PLAYER) {
            return;
        }
        try {
            playerMapper.update(null, new LambdaUpdateWrapper<Player>()
                    .eq(Player::getId, player.getId())
                    .eq(Player::getType, PlayerType.GUEST)
                    .set(Player::getType, PlayerType.PLAYER));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_JERSEY);
        }
    }
}
