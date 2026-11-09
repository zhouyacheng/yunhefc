package com.yunhefc.notification.application;

import com.yunhefc.contracts.notification.NotificationReadPort;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import org.springframework.stereotype.Component;

@Component
public class NotificationReadAdapter implements NotificationReadPort {

    private final NotificationMapper notificationMapper;

    public NotificationReadAdapter(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Override
    public int countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }
}
