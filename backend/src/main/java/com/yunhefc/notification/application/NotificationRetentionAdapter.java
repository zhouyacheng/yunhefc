package com.yunhefc.notification.application;

import com.yunhefc.common.config.RetentionProperties;
import com.yunhefc.common.retention.RetentionTask;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Order(1)
@RequiredArgsConstructor
public class NotificationRetentionAdapter implements RetentionTask {

    private final NotificationMapper notificationMapper;
    private final RetentionProperties retentionProperties;

    @Override
    public String name() {
        return "notification";
    }

    @Override
    public int deleteBatch(Instant now, int limit) {
        return notificationMapper.deleteCreatedBefore(
                now.minus(Duration.ofDays(retentionProperties.getNotificationDays())), limit);
    }
}
