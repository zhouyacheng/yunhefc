package com.yunhefc.player.application;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.infrastructure.PlayerMapper;
import org.springframework.stereotype.Service;

/**
 * 注册建档与转正编排中供 auth 调用的球员档案写入口。
 */
@Service
public class PlayerProfileApplicationService {

    private final PlayerMapper playerMapper;

    public PlayerProfileApplicationService(PlayerMapper playerMapper) {
        this.playerMapper = playerMapper;
    }

    public PlayerSnapshot createProfile(Long teamId, Long userId, String name, int type, int position) {
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
        if (player.getType() == 1) {
            return;
        }
        player.setType(1);
        playerMapper.updateById(player);
    }
}
