package com.yunhefc.notification.dto;

import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    /**
     * 05 §4 Notification：枚举返回数字 code 与只读 typeLabel。
     */
    public record NotificationView(
            Long id,
            int type,
            String typeLabel,
            String title,
            String body,
            String refType,
            Long refId,
            Instant readAt,
            Instant createdAt) {
    }

    public record UnreadCountView(int count) {
    }

    public record ReadResult(Instant readAt) {
    }

    public record ReadAllResult(int updated) {
    }
}
