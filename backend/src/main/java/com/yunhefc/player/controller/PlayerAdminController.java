package com.yunhefc.player.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.player.application.PlayerAdminApplicationService;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminQuery;
import com.yunhefc.player.dto.PlayerDtos.PlayerAdminView;
import com.yunhefc.player.dto.PlayerDtos.PlayerUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/players")
@RequiredArgsConstructor
public class PlayerAdminController {

    private final PlayerAdminApplicationService playerAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('player:view:any')")
    public ApiResponse<PageResponse<PlayerAdminView>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Boolean admin,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer position,
            @RequestParam(required = false) Integer skill,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        PlayerAdminQuery query = new PlayerAdminQuery(
                name, region, type, admin, status, position, skill, sort, page, pageSize);
        return ApiResponse.ok(playerAdminService.list(query));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('player:edit:any')")
    public ApiResponse<PlayerAdminView> update(@PathVariable Long id,
                                               @Valid @RequestBody PlayerUpdateRequest request) {
        return ApiResponse.ok(playerAdminService.update(id, request));
    }
}
