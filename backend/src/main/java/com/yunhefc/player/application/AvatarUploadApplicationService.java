package com.yunhefc.player.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.util.Ulid;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.infra.storage.OssGateway;
import com.yunhefc.player.domain.AvatarImage;
import com.yunhefc.player.domain.Player;
import com.yunhefc.player.dto.PlayerDtos.AvatarUploadView;
import com.yunhefc.player.infrastructure.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 头像只上传到私有 OSS，不修改球员档案；绑定走 PLR-03/PLR-06。
 */
@Service
@RequiredArgsConstructor
public class AvatarUploadApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AvatarUploadApplicationService.class);

    private final OssGateway ossGateway;
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
        byte[] content = read(file);
        AvatarImage.Format format = AvatarImage.validate(content);
        String objectKey = "avatar/" + userId + "/" + Ulid.generate() + "." + format.extension();
        try {
            ossGateway.putObject(objectKey, content, format.contentType());
        } catch (RuntimeException ex) {
            log.warn("avatar upload failed, objectKey={}", objectKey, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new AvatarUploadView(objectKey, ossGateway.signedUrl(objectKey));
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AvatarImage.invalidFile(AvatarImage.EMPTY_MESSAGE);
        }
        if (file.getSize() > AvatarImage.MAX_BYTES) {
            throw AvatarImage.invalidFile(AvatarImage.TOO_LARGE_MESSAGE);
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw AvatarImage.invalidFile(AvatarImage.CORRUPT_MESSAGE);
        }
    }
}
