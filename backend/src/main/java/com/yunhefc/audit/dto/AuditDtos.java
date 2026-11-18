package com.yunhefc.audit.dto;

import java.time.LocalDateTime;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditLogQuery(String actor, String action, String targetType, Long targetId,
                                LocalDateTime from, LocalDateTime to, long page, long pageSize) {
    }

    public record AuditLogView(Long id, Actor actor, String action, String targetType, Long targetId,
                               Object beforeRedacted, Object afterRedacted, String requestId,
                               String ipMasked, LocalDateTime createdAt) {

        public record Actor(Long id, String name) {
        }
    }
}
