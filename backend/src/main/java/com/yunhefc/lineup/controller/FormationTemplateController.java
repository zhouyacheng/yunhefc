package com.yunhefc.lineup.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.lineup.application.FormationTemplateApplicationService;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateCreateRequest;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateListResponse;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateRenameRequest;
import com.yunhefc.lineup.dto.LineupDtos.FormationTemplateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/formation-templates")
@RequiredArgsConstructor
public class FormationTemplateController {

    private final FormationTemplateApplicationService formationTemplateApplicationService;

    @GetMapping
    @PreAuthorize("hasAuthority('lineup:edit:any')")
    public ApiResponse<FormationTemplateListResponse> list(
            @RequestParam(defaultValue = "true") boolean includePreset) {
        return ApiResponse.ok(new FormationTemplateListResponse(
                formationTemplateApplicationService.list(includePreset)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('lineup:edit:any')")
    public ApiResponse<FormationTemplateResponse> create(
            @Valid @RequestBody FormationTemplateCreateRequest request) {
        return ApiResponse.ok(new FormationTemplateResponse(
                formationTemplateApplicationService.create(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('lineup:edit:any')")
    public ApiResponse<FormationTemplateResponse> rename(
            @PathVariable Long id,
            @Valid @RequestBody FormationTemplateRenameRequest request) {
        return ApiResponse.ok(new FormationTemplateResponse(
                formationTemplateApplicationService.rename(id, request.name())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('lineup:edit:any')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        formationTemplateApplicationService.delete(id);
        return ApiResponse.ok();
    }
}
