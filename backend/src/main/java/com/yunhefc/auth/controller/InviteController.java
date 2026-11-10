package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.AuthService;
import com.yunhefc.auth.application.InviteService;
import com.yunhefc.auth.dto.AuthDtos.InviteCreateRequest;
import com.yunhefc.auth.dto.AuthDtos.InviteCreatedView;
import com.yunhefc.auth.dto.AuthDtos.InviteView;
import com.yunhefc.auth.dto.AuthDtos.MatchInviteCreateRequest;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final AuthService authService;

    @GetMapping("/c/invites/{token}")
    public ApiResponse<InviteView> resolve(@PathVariable String token) {
        return ApiResponse.ok(inviteService.resolve(token));
    }

    @PostMapping("/c/invites/{token}/accept")
    public ApiResponse<Map<String, Object>> accept(@PathVariable String token) {
        Long userId = CurrentUser.principal().userId();
        InviteView invite = inviteService.accept(token, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("player", authService.me(userId).player());
        data.put("matchId", invite.match() == null ? null : invite.match().id());
        return ApiResponse.ok(data);
    }

    @PostMapping("/c/matches/{id}/invites")
    @PreAuthorize("hasAuthority('invite:create:match')")
    @Idempotent("INV-03")
    public ApiResponse<InviteCreatedView> createMatchInvite(@PathVariable Long id,
                                                            @Valid @RequestBody MatchInviteCreateRequest request) {
        InviteCreatedView invite = inviteService.createMatchInvite(
                CurrentUser.principal().userId(), id, request.expiresInDays(), request.maxUses());
        return ApiResponse.ok(invite);
    }

    @GetMapping("/b/invites")
    @PreAuthorize("hasAuthority('invite:manage:any')")
    public ApiResponse<PageResponse<InviteView>> list(
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        PageQuery query = PageQuery.of(page, pageSize);
        return ApiResponse.ok(inviteService.list(type, status, query.page(), query.pageSize()));
    }

    @PostMapping("/b/invites")
    @PreAuthorize("hasAuthority('invite:manage:any')")
    @Idempotent("INV-05")
    public ApiResponse<InviteCreatedView> create(@Valid @RequestBody InviteCreateRequest request) {
        return ApiResponse.ok(inviteService.create(CurrentUser.principal().userId(), request));
    }

    @PostMapping("/b/invites/{id}/revoke")
    @PreAuthorize("hasAuthority('invite:manage:any')")
    @Idempotent("INV-06")
    public ApiResponse<Map<String, Integer>> revoke(@PathVariable Long id) {
        inviteService.revoke(id);
        return ApiResponse.ok(Map.of("status", 1));
    }
}
