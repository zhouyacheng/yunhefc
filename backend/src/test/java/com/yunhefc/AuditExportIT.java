package com.yunhefc;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.application.AuditExportApplicationService;
import com.yunhefc.audit.infrastructure.AuditExportJob;
import com.yunhefc.audit.infrastructure.AuditExportProperties;
import com.yunhefc.infra.storage.InMemoryOssGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Import(AuditExportIT.FixedClockConfig.class)
class AuditExportIT extends IntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int YEAR = 2026;
    private static final String OBJECT_KEY = "audit/export/" + YEAR + "/audit-" + YEAR + ".jsonl.gz";

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private AuditExportApplicationService exportService;

    @Autowired
    private Environment environment;

    @Autowired
    private AuditExportProperties exportProperties;

    @Autowired
    private InMemoryOssGateway ossGateway;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void resetExportState() {
        exportProperties.setEnabled(false);
        ossGateway.deleteObject(OBJECT_KEY);
    }

    @AfterEach
    void restoreDefaultSwitch() {
        exportProperties.setEnabled(false);
    }

    @Test
    void exportsPreviousYearAcrossTeamsAsOrderedJsonlWithRawObjectSnapshots() throws Exception {
        Instant midYear = LocalDate.of(YEAR, 6, 1).atStartOfDay(ZONE).toInstant();
        Long first = insertAudit(1L, 101L, "张三", "MATCH_CREATE", "MATCH", 501L,
                null, "{\"phone\":\"13812345678\",\"nested\":{\"a\":1}}",
                "export-a", "10.0.0.1", midYear.plusSeconds(200));
        Long second = insertAudit(2L, null, "系统", "INVITE_EXPIRE", "INVITE", 502L,
                "{\"status\":0}", "{\"status\":2}", "export-b", null, midYear.plusSeconds(100));
        Long third = insertAudit(1L, 102L, "李四", "MATCH_UPDATE", "MATCH", 501L,
                null, null, "export-c", null, midYear);

        exportProperties.setEnabled(true);
        exportService.exportPreviousYear();

        assertThat(ossGateway.exists(OBJECT_KEY)).isTrue();
        assertThat(ossGateway.contentType(OBJECT_KEY)).isEqualTo("application/gzip");

        List<JsonNode> rows = readLines(ossGateway.getObject(OBJECT_KEY));
        assertThat(rows).extracting(row -> row.get("id").asLong())
                .containsExactly(first, second, third);

        JsonNode firstRow = rows.get(0);
        assertThat(firstRow.get("teamId").asLong()).isEqualTo(1L);
        assertThat(firstRow.get("actorUserId").asLong()).isEqualTo(101L);
        assertThat(firstRow.get("actorName").asText()).isEqualTo("张三");
        assertThat(firstRow.get("action").asText()).isEqualTo("MATCH_CREATE");
        assertThat(firstRow.get("targetType").asText()).isEqualTo("MATCH");
        assertThat(firstRow.get("targetId").asLong()).isEqualTo(501L);
        assertThat(firstRow.get("requestId").asText()).isEqualTo("export-a");
        assertThat(firstRow.get("ip").asText()).isEqualTo("10.0.0.1");
        assertThat(Instant.parse(firstRow.get("createdAt").asText()))
                .isEqualTo(midYear.plusSeconds(200));
        assertThat(firstRow.get("before").isNull()).isTrue();
        assertThat(firstRow.get("after").isObject()).isTrue();
        assertThat(firstRow.get("after").get("phone").asText()).isEqualTo("13812345678");
        assertThat(firstRow.get("after").get("nested").get("a").asInt()).isEqualTo(1);

        JsonNode secondRow = rows.get(1);
        assertThat(secondRow.get("teamId").asLong()).isEqualTo(2L);
        assertThat(secondRow.get("actorUserId").isNull()).isTrue();
        assertThat(secondRow.get("actorName").asText()).isEqualTo("系统");
        assertThat(secondRow.get("before").isObject()).isTrue();
        assertThat(secondRow.get("after").get("status").asInt()).isEqualTo(2);
    }

    @Test
    void exportsOnlyRowsInsideShanghaiCalendarYear() {
        Instant start = LocalDate.of(YEAR, 1, 1).atStartOfDay(ZONE).toInstant();
        Instant end = LocalDate.of(YEAR + 1, 1, 1).atStartOfDay(ZONE).toInstant();
        insertAudit(1L, 201L, "边界前", "ACTION", "MATCH", 1L, null, null, "export-before",
                null, start.minusMillis(1));
        Long atStart = insertAudit(1L, 201L, "边界起", "ACTION", "MATCH", 1L, null, null,
                "export-start", null, start);
        insertAudit(1L, 201L, "边界后", "ACTION", "MATCH", 1L, null, null, "export-end",
                null, end);

        exportProperties.setEnabled(true);
        exportService.exportPreviousYear();

        assertThat(readLines(ossGateway.getObject(OBJECT_KEY)))
                .extracting(row -> row.get("id").asLong())
                .containsExactly(atStart);
    }

    @Test
    void skipsYearWhenObjectAlreadyExists() throws Exception {
        insertAudit(1L, 301L, "幂等", "ACTION", "MATCH", 1L, null, null, "export-idempotent",
                null, LocalDate.of(YEAR, 7, 1).atStartOfDay(ZONE).toInstant());
        byte[] sentinel = "already-exported".getBytes(StandardCharsets.UTF_8);
        ossGateway.putObject(OBJECT_KEY, sentinel, "application/gzip");

        exportProperties.setEnabled(true);
        exportService.exportPreviousYear();

        assertThat(ossGateway.getObject(OBJECT_KEY)).isEqualTo(sentinel);
    }

    @Test
    void switchDefaultsToDisabled() {
        assertThat(environment.getProperty("yunhefc.audit.export.enabled", Boolean.class)).isFalse();
    }

    @Test
    void writesNothingWhenDisabled() {
        insertAudit(1L, 401L, "关闭", "ACTION", "MATCH", 1L, null, null, "export-disabled",
                null, LocalDate.of(YEAR, 8, 1).atStartOfDay(ZONE).toInstant());

        exportProperties.setEnabled(false);
        exportService.exportPreviousYear();

        assertThat(ossGateway.exists(OBJECT_KEY)).isFalse();
    }

    @Test
    void schedulesOncePerYearOnJanuaryFirstInShanghaiTime() {
        JobDetail detail = context.getBean("auditExportJobDetail", JobDetail.class);
        assertThat(detail.getJobClass()).isEqualTo(AuditExportJob.class);
        assertThat(detail.getKey()).isEqualTo(JobKey.jobKey("auditExportJob", "yunhefc"));

        Trigger trigger = context.getBean("auditExportTrigger", Trigger.class);
        assertThat(trigger).isInstanceOf(CronTrigger.class);
        CronTrigger cronTrigger = (CronTrigger) trigger;
        assertThat(cronTrigger.getCronExpression()).isEqualTo("0 0 0 1 1 ?");
        assertThat(cronTrigger.getTimeZone().getID()).isEqualTo("Asia/Shanghai");
        assertThat(trigger.getKey()).isEqualTo(TriggerKey.triggerKey("auditExportTrigger", "yunhefc"));
        assertThat(trigger.getJobKey()).isEqualTo(detail.getKey());
    }

    private List<JsonNode> readLines(byte[] content) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(content)), StandardCharsets.UTF_8))) {
            return reader.lines().map(this::readJson).toList();
        } catch (IOException ex) {
            throw new IllegalStateException("导出内容不是可解的 gzip/JSONL", ex);
        }
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            throw new IllegalStateException("导出行不是合法 JSON: " + line, ex);
        }
    }

    private Long insertAudit(Long teamId, Long actorUserId, String actorName, String action,
                             String targetType, Long targetId, String before, String after,
                             String requestId, String ip, Instant createdAt) {
        jdbc.update("""
                INSERT INTO audit_log (team_id, actor_user_id, actor_name, action, target_type,
                    target_id, before_json, after_json, request_id, ip, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, teamId, actorUserId, actorName, action, targetType, targetId,
                before, after, requestId, ip, Timestamp.from(createdAt));
        return jdbc.queryForObject("SELECT id FROM audit_log WHERE request_id = ?", Long.class, requestId);
    }
}
