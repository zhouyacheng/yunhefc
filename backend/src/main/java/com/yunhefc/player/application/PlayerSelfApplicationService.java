package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminView;
import com.yunhefc.player.dto.PlayerDtos.PlayerSelfUpdateRequest;
import com.yunhefc.player.dto.PlayerDtos.PlayerUpdateRequest;
import com.yunhefc.player.dto.PlayerDtos.PlayerView;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C 端本人自助维护姓名、地区、主副位置与头像；号码、星级、类型与角色不可自助修改。
 */
@Service
@RequiredArgsConstructor
public class PlayerSelfApplicationService {

    private final PlayerMapper playerMapper;
    private final PlayerAdminApplicationService playerAdminApplicationService;

    @Transactional
    public PlayerView update(PlayerSelfUpdateRequest request) {
        if (!request.getUnknownFields().isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            for (String field : request.getUnknownFields()) {
                details.put(field, "不允许自助修改");
            }
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    ErrorCode.VALIDATION_FAILED.message(), Map.of("details", details));
        }

        AuthPrincipal principal = CurrentUser.principal();
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getUserId, principal.userId())
                .eq(Player::getTeamId, principal.teamId()));
        if (player == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在");
        }

        PlayerAdminView updated = playerAdminApplicationService.update(player.getId(),
                new PlayerUpdateRequest(
                        request.getName(),
                        null,
                        request.getRegion(),
                        request.getPosition(),
                        request.getSecondaryPositions(),
                        null,
                        request.getAvatarUrl()));
        return PlayerView.from(updated);
    }
}
