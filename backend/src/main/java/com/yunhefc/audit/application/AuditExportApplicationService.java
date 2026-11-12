package com.yunhefc.audit.application;

import com.yunhefc.audit.domain.AuditLog;
import com.yunhefc.audit.infrastructure.AuditExportProperties;
import com.yunhefc.infra.storage.OssGateway;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 每年 1 月 1 日把上一自然年的审计日志冷备到私有 OSS；快照保留原文，不做脱敏。
 */
@Service
@RequiredArgsConstructor
public class AuditExportApplicationService {

    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Logger log = LoggerFactory.getLogger(AuditExportApplicationService.class);

    private final AuditLogApplicationService auditLogApplicationService;
    private final AuditExportSerializer serializer;
    private final OssGateway ossGateway;
    private final Clock clock;
    private final AuditExportProperties properties;

    public void exportPreviousYear() {
        if (!properties.isEnabled()) {
            return;
        }
        int year = LocalDate.now(clock.withZone(ZONE)).minusYears(1).getYear();
        try {
            exportYear(year);
        } catch (Exception ex) {
            log.error("audit export failed, year={}", year, ex);
        }
    }

    private void exportYear(int year) {
        String objectKey = objectKey(year);
        if (ossGateway.exists(objectKey)) {
            log.debug("audit export already exists, year={}", year);
            return;
        }
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(ZONE).toInstant();
        Instant to = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZONE).toInstant();
        List<AuditLog> rows = auditLogApplicationService.exportRows(from, to);
        ossGateway.putObject(objectKey, serializer.toJsonlGz(rows), "application/gzip");
        log.debug("audit export written, year={}, rows={}", year, rows.size());
    }

    static String objectKey(int year) {
        return "audit/export/" + year + "/audit-" + year + ".jsonl.gz";
    }
}
