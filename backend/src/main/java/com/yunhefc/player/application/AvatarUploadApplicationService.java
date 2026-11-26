package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.storage.ImagePolicy;
import com.yunhefc.infra.storage.ImageStore;
import com.yunhefc.infra.storage.StoredImage;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.dto.PlayerDtos.AvatarUploadView;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 头像只上传到私有 OSS，不修改球员档案；绑定走 PLR-03/PLR-06。
 */
@Service
@RequiredArgsConstructor
public class AvatarUploadApplicationService {

    private static final ImagePolicy AVATAR_IMAGE_POLICY =
            new ImagePolicy("avatar", 5_242_880L, "图片不能超过 5MB");

    private final ImageStore imageStore;
    private final PlayerMapper playerMapper;

    public AvatarUploadView uploadOwn(MultipartFile file) {
        AuthPrincipal principal = CurrentUser.principal();
        return store(principal.userId(), file);
    }

    public AvatarUploadView uploadForPlayer(Long playerId, MultipartFile file) {
        AuthPrincipal principal = CurrentUser.principal();
        Player player = playerMapper.selectOne(new LambdaQueryWrapper<Player>()
                .eq(Player::getId, playerId)
                .eq(Player::getTeamId, principal.teamId()));
        if (player == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "球员档案不存在");
        }
        return store(player.getUserId(), file);
    }

    private AvatarUploadView store(Long userId, MultipartFile file) {
        StoredImage stored = imageStore.store(file, AVATAR_IMAGE_POLICY, userId);
        return new AvatarUploadView(stored.objectKey(), stored.signedUrl());
    }
}
