package com.yunhefc.audit.controller;

import com.yunhefc.audit.application.AuditLogApplicationService;
import com.yunhefc.audit.dto.AuditDtos.AuditLogQuery;
import com.yunhefc.audit.dto.AuditDtos.AuditLogView;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/b/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogApplicationService auditLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view:any')")
    public ApiResponse<PageResponse<AuditLogView>> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        AuditLogQuery query = new AuditLogQuery(
                actor, action, targetType, targetId, instant(from), instant(to), page, pageSize);
        return ApiResponse.ok(auditLogService.list(query));
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "时间必须为 ISO-8601 UTC 格式");
        }
    }
}
