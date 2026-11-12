package com.yunhefc.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.audit.domain.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * 审计冷备格式：gzip 压缩的 JSONL，一行一条，快照为 JSON 对象且保留原文。
 */
@Component
@RequiredArgsConstructor
public class AuditExportSerializer {

    private final ObjectMapper objectMapper;

    public byte[] toJsonlGz(List<AuditLog> rows) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer writer = new OutputStreamWriter(
                new GZIPOutputStream(bytes), StandardCharsets.UTF_8)) {
            for (AuditLog row : rows) {
                writer.write(objectMapper.writeValueAsString(toLine(row)));
                writer.write('\n');
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return bytes.toByteArray();
    }

    private ExportLine toLine(AuditLog row) {
        return new ExportLine(
                row.getId(), row.getTeamId(), row.getActorUserId(), row.getActorName(),
                row.getAction(), row.getTargetType(), row.getTargetId(),
                parseSnapshot(row.getBeforeJson()), parseSnapshot(row.getAfterJson()),
                row.getRequestId(), row.getIp(), row.getCreatedAt());
    }

    private Object parseSnapshot(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    record ExportLine(Long id, Long teamId, Long actorUserId, String actorName, String action,
                      String targetType, Long targetId, Object before, Object after,
                      String requestId, String ip, Instant createdAt) {
    }
}
