package com.yunhefc.player.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.player.application.PlayerSelfApplicationService;
import com.yunhefc.player.dto.PlayerDtos.PlayerSelfUpdateRequest;
import com.yunhefc.player.dto.PlayerDtos.PlayerView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/me/player")
@RequiredArgsConstructor
public class PlayerSelfController {

    private final PlayerSelfApplicationService playerSelfService;

    @PatchMapping
    @PreAuthorize("hasAuthority('player:edit:own')")
    public ApiResponse<PlayerView> update(@Valid @RequestBody PlayerSelfUpdateRequest request) {
        return ApiResponse.ok(playerSelfService.update(request));
    }
}
