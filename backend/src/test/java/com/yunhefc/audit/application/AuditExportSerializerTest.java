package com.yunhefc.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yunhefc.audit.domain.AuditLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AuditExportSerializerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final AuditExportSerializer serializer = new AuditExportSerializer(objectMapper);

    @Test
    void writesOneJsonObjectPerLineWithObjectSnapshotsInGivenOrder() throws Exception {
        AuditLog first = row(7L, 1L, 11L, "张三", "MATCH_CREATE", "MATCH", 501L,
                null, "{\"phone\":\"13812345678\"}", "req-a", "10.0.0.1",
                Instant.parse("2026-03-01T10:00:00Z"));
        AuditLog second = row(9L, 2L, null, "系统", "INVITE_EXPIRE", "INVITE", 502L,
                "{\"status\":0}", "{\"status\":2}", "req-b", null,
                Instant.parse("2026-12-31T23:59:59.999Z"));

        String raw = gunzip(serializer.toJsonlGz(List.of(first, second)));
        assertThat(raw).endsWith("\n");

        List<JsonNode> lines = raw.lines().map(this::readJson).toList();
        assertThat(lines).extracting(line -> line.get("id").asLong()).containsExactly(7L, 9L);

        JsonNode firstLine = lines.get(0);
        assertThat(firstLine.get("before").isNull()).isTrue();
        assertThat(firstLine.get("after").isObject()).isTrue();
        assertThat(firstLine.get("after").get("phone").asText()).isEqualTo("13812345678");
        assertThat(firstLine.get("createdAt").asText()).isEqualTo("2026-03-01T10:00:00Z");
        assertThat(firstLine.get("ip").asText()).isEqualTo("10.0.0.1");

        JsonNode secondLine = lines.get(1);
        assertThat(secondLine.get("actorUserId").isNull()).isTrue();
        assertThat(secondLine.get("actorName").asText()).isEqualTo("系统");
        assertThat(secondLine.get("before").isObject()).isTrue();
        assertThat(secondLine.get("after").get("status").asInt()).isEqualTo(2);
    }

    private String gunzip(byte[] content) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ex) {
            throw new IllegalStateException("导出行不是合法 JSON: " + line, ex);
        }
    }

    private AuditLog row(Long id, Long teamId, Long actorUserId, String actorName, String action,
                         String targetType, Long targetId, String before, String after,
                         String requestId, String ip, Instant createdAt) {
        AuditLog row = new AuditLog();
        row.setId(id);
        row.setTeamId(teamId);
        row.setActorUserId(actorUserId);
        row.setActorName(actorName);
        row.setAction(action);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setBeforeJson(before);
        row.setAfterJson(after);
        row.setRequestId(requestId);
        row.setIp(ip);
        row.setCreatedAt(createdAt);
        return row;
    }
}
