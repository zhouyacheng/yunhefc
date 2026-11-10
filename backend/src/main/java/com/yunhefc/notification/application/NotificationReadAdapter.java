package com.yunhefc.notification.application;

import com.yunhefc.contracts.notification.NotificationReadPort;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationReadAdapter implements NotificationReadPort {

    private final NotificationMapper notificationMapper;

    @Override
    public int countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }
}
