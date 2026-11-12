package com.yunhefc.notification.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.notification.application.NotificationApplicationService;
import com.yunhefc.notification.dto.NotificationDtos.NotificationView;
import com.yunhefc.notification.dto.NotificationDtos.ReadAllResult;
import com.yunhefc.notification.dto.NotificationDtos.ReadResult;
import com.yunhefc.notification.dto.NotificationDtos.UnreadCountView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationApplicationService notificationService;

    @GetMapping
    public ApiResponse<PageResponse<NotificationView>> list(
            @RequestParam(required = false) Boolean unread,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(notificationService.list(unread, page, pageSize));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountView> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount());
    }

    @PostMapping("/{id}/read")
    public ApiResponse<ReadResult> markRead(@PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(id));
    }

    @PostMapping("/read-all")
    public ApiResponse<ReadAllResult> markAllRead() {
        return ApiResponse.ok(notificationService.markAllRead());
    }
}
