package com.yunhefc.query.controller;

import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.query.application.MyRegistrationsApplicationService;
import com.yunhefc.query.dto.MyRegistrationDtos.MyMatchRegistrationView;
import com.yunhefc.query.dto.MyRegistrationDtos.MyRegistration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class MyRegistrationsController {

    private final MyRegistrationsApplicationService myRegistrationsService;

    @GetMapping("/me/registrations")
    public ApiResponse<PageResponse<MyRegistration>> list(
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResponse.ok(myRegistrationsService.list(scope, page, pageSize));
    }

    @GetMapping("/matches/{matchId}/registrations/me")
    public ApiResponse<MyMatchRegistrationView> mine(@PathVariable Long matchId) {
        return ApiResponse.ok(myRegistrationsService.mineForMatch(matchId));
    }
}
