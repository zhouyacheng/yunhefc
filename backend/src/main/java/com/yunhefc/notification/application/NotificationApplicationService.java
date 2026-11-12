package com.yunhefc.notification.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.notification.domain.Notification;
import com.yunhefc.notification.dto.NotificationDtos.NotificationView;
import com.yunhefc.notification.dto.NotificationDtos.ReadAllResult;
import com.yunhefc.notification.dto.NotificationDtos.ReadResult;
import com.yunhefc.notification.dto.NotificationDtos.UnreadCountView;
import com.yunhefc.notification.infrastructure.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationApplicationService {

    private final NotificationMapper notificationMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(Boolean unread, long page, long pageSize) {
        Long userId = CurrentUser.principal().userId();
        PageQuery query = PageQuery.of(page, pageSize);
        Page<Notification> result = notificationMapper.selectPage(
                new Page<>(query.page(), query.pageSize()),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .isNull(Boolean.TRUE.equals(unread), Notification::getReadAt)
                        .orderByDesc(Notification::getCreatedAt)
                        .orderByDesc(Notification::getId));
        List<NotificationView> items = result.getRecords().stream()
                .map(NotificationApplicationService::toView)
                .toList();
        return PageResponse.of(items, result.getTotal(), query.page(), query.pageSize());
    }

    public UnreadCountView unreadCount() {
        return new UnreadCountView(notificationMapper.countUnread(CurrentUser.principal().userId()));
    }

    /**
     * 只按 id + userId 定位；重复已读返回原时间，不覆盖。
     */
    @Transactional
    public ReadResult markRead(Long notificationId) {
        Long userId = CurrentUser.principal().userId();
        Notification notification = requireOwned(userId, notificationId);
        if (notification.getReadAt() != null) {
            return new ReadResult(notification.getReadAt());
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, now));
        // 并发重复已读时以先写入的时间为准
        return new ReadResult(requireOwned(userId, notificationId).getReadAt());
    }

    @Transactional
    public ReadAllResult markAllRead() {
        Long userId = CurrentUser.principal().userId();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, now));
        return new ReadAllResult(updated);
    }

    private Notification requireOwned(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId));
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        return notification;
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType().code(),
                notification.getType().label(),
                notification.getTitle(),
                notification.getBody(),
                notification.getRefType(),
                notification.getRefId(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
