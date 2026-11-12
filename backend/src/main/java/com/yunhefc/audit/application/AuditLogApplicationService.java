package com.yunhefc.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.domain.AuditLog;
import com.yunhefc.audit.dto.AuditDtos.AuditLogQuery;
import com.yunhefc.audit.dto.AuditDtos.AuditLogView;
import com.yunhefc.audit.infrastructure.AuditLogMapper;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.util.MaskUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计日志只读查询；不提供 update/delete。
 */
@Service
@RequiredArgsConstructor
public class AuditLogApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogApplicationService.class);

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> list(AuditLogQuery query) {
        Long teamId = CurrentUser.principal().teamId();
        PageQuery page = PageQuery.of(query.page(), query.pageSize());

        String actor = normalize(query.actor());
        Long actorUserId = null;
        String actorName = null;
        if (actor != null) {
            if (actor.matches("\\d+")) {
                try {
                    actorUserId = Long.parseLong(actor);
                } catch (NumberFormatException ex) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "操作人用户 ID 不合法");
                }
            } else {
                if (actor.length() > 64) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "操作人姓名不能超过 64 个字符");
                }
                actorName = actor;
            }
        }
        if (query.targetId() != null && query.targetId() < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "对象 ID 必须大于等于 1");
        }
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "开始时间不能晚于结束时间");
        }
        String action = normalize(query.action());
        String targetType = normalize(query.targetType());

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getTeamId, teamId)
                .eq(actorUserId != null, AuditLog::getActorUserId, actorUserId)
                .like(actorName != null, AuditLog::getActorName, actorName)
                .eq(action != null, AuditLog::getAction, action)
                .eq(targetType != null, AuditLog::getTargetType, targetType)
                .eq(query.targetId() != null, AuditLog::getTargetId, query.targetId())
                .ge(query.from() != null, AuditLog::getCreatedAt, query.from())
                .le(query.to() != null, AuditLog::getCreatedAt, query.to())
                .orderByDesc(AuditLog::getCreatedAt)
                .orderByDesc(AuditLog::getId);

        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page.page(), page.pageSize()), wrapper);
        List<AuditLogView> items = result.getRecords().stream().map(this::toView).toList();
        return PageResponse.of(items, result.getTotal(), page.page(), page.pageSize());
    }

    private AuditLogView toView(AuditLog log) {
        String actorName = log.getActorName();
        if ((actorName == null || actorName.isBlank()) && log.getActorUserId() == null) {
            actorName = AuditRecorder.SYSTEM_ACTOR_NAME;
        }
        return new AuditLogView(
                log.getId(),
                new AuditLogView.Actor(log.getActorUserId(), actorName),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                redact(log.getBeforeJson()),
                redact(log.getAfterJson()),
                log.getRequestId(),
                MaskUtils.maskIp(log.getIp()),
                log.getCreatedAt());
    }

    private Object redact(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MaskUtils.mask(objectMapper.readValue(json, Object.class));
        } catch (JsonProcessingException ex) {
            log.warn("无法解析审计快照 JSON，响应按空快照返回", ex);
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
