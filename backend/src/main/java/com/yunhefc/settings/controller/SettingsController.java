package com.yunhefc.settings.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.settings.application.SettingsApplicationService;
import com.yunhefc.settings.dto.SettingsDtos.SettingsResponse;
import com.yunhefc.settings.dto.SettingsDtos.SettingsUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsApplicationService settingsApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('settings:view:any')")
    public ApiResponse<SettingsResponse> get() {
        return ApiResponse.ok(new SettingsResponse(settingsApplicationService.get()));
    }

    @PatchMapping
    @PreAuthorize("hasAuthority('settings:manage:any')")
    @Idempotent("SET-02")
    public ApiResponse<SettingsResponse> update(@Valid @RequestBody SettingsUpdateRequest request) {
        return ApiResponse.ok(new SettingsResponse(
                settingsApplicationService.update(request.settings())));
    }
}
