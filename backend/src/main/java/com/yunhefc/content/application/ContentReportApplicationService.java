package com.yunhefc.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.messaging.EventTypes;
import com.yunhefc.content.domain.ContentPost;
import com.yunhefc.content.domain.ContentReportTarget;
import com.yunhefc.content.domain.ContentRules;
import com.yunhefc.content.dto.ContentDtos.ContentReportRequest;
import com.yunhefc.content.dto.ContentDtos.ContentReportResult;
import com.yunhefc.content.infrastructure.ContentAlbumPhotoMapper;
import com.yunhefc.content.infrastructure.ContentPostMapper;
import com.yunhefc.infra.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CNT-13 内容举报：不建举报表，经 Outbox 通知持 content:delete:any 的正常账号（01 M17）。
 */
@Service
@RequiredArgsConstructor
public class ContentReportApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ContentReportApplicationService.class);

    private final ContentPostMapper postMapper;
    private final ContentAlbumPhotoMapper photoMapper;
    private final OutboxService outboxService;

    @Transactional
    public ContentReportResult report(ContentReportRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        ContentReportTarget target = ContentReportTarget.of(request.targetType());
        String reason = ContentRules.normalizeReportReason(request.reason());
        Long refId = requireVisibleTarget(teamId, target, request.targetId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("targetType", target.code());
        payload.put("targetId", request.targetId());
        // 通知落点：动态 ID，或照片所属相册 ID。
        payload.put("refId", refId);
        if (reason != null) {
            payload.put("reason", reason);
        }
        outboxService.append(teamId, target.refType(), request.targetId(),
                EventTypes.CONTENT_REPORTED, payload);
        log.info("content reported, targetType={}, targetId={}, teamId={}",
                target.code(), request.targetId(), teamId);
        return new ContentReportResult(true);
    }

    /**
     * 校验目标存在且可见，返回通知落点 ID。
     */
    private Long requireVisibleTarget(Long teamId, ContentReportTarget target, Long targetId) {
        if (target == ContentReportTarget.POST) {
            ContentPost post = postMapper.selectOne(new LambdaQueryWrapper<ContentPost>()
                    .eq(ContentPost::getId, targetId)
                    .eq(ContentPost::getTeamId, teamId));
            if (post == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在或已删除");
            }
            return targetId;
        }
        Long albumId = photoMapper.selectVisibleAlbumId(teamId, targetId);
        if (albumId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "照片不存在或已删除");
        }
        return albumId;
    }
}
