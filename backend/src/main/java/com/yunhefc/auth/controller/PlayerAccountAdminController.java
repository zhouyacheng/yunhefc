package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.AccountLifecycleApplicationService;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M02 球员页的账号生命周期动作；编排由 auth 应用服务承担（Auth → Player）。
 */
@RestController
@RequestMapping("/api/v1/b/players")
@RequiredArgsConstructor
public class PlayerAccountAdminController {

    private final AccountLifecycleApplicationService accountLifecycleService;

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('user:approve:any')")
    @Idempotent("PLR-07")
    public ApiResponse<PlayerAdminView> approve(@PathVariable Long id) {
        return ApiResponse.ok(accountLifecycleService.approve(id));
    }
}
