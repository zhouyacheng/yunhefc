package com.yunhefc.registration.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.idempotency.Idempotent;
import com.yunhefc.registration.application.RegistrationApplicationService;
import com.yunhefc.registration.dto.RegistrationDtos.RegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c/matches/{matchId}/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationApplicationService registrationApplicationService;

    @PostMapping
    @PreAuthorize("hasAuthority('registration:create:own')")
    @Idempotent("REG-01")
    public ApiResponse<RegistrationResponse> register(@PathVariable Long matchId) {
        return ApiResponse.ok(
                new RegistrationResponse(registrationApplicationService.register(matchId)));
    }
}
