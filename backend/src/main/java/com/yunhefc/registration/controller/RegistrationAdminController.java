package com.yunhefc.registration.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.registration.application.RegistrationAdminApplicationService;
import com.yunhefc.registration.dto.RegistrationDtos.RemovalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/b/registrations")
@RequiredArgsConstructor
public class RegistrationAdminController {

    private final RegistrationAdminApplicationService registrationAdminApplicationService;

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('registration:remove:any')")
    @Idempotent("REG-07")
    public ApiResponse<RemovalResult> remove(@PathVariable Long id) {
        return ApiResponse.ok(registrationAdminApplicationService.remove(id));
    }
}
