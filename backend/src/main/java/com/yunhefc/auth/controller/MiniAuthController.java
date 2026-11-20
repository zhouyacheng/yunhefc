package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.MiniAuthService;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.MiniDtos.MiniBindPhoneRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginResponse;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.ratelimit.RedisRateLimiter;
import com.yunhefc.common.security.Hashing;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/c/auth/mini")
@RequiredArgsConstructor
public class MiniAuthController {

    private static final int LOGIN_LIMIT = 30;
    private static final int BIND_LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MiniAuthService miniAuthService;
    private final RedisRateLimiter rateLimiter;

    @PostMapping("/login")
    public ApiResponse<MiniLoginResponse> login(@Valid @RequestBody MiniLoginRequest request,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse response) {
        rateLimiter.check("mini-login:" + servletRequest.getRemoteAddr(), LOGIN_LIMIT, WINDOW);
        return ApiResponse.ok(miniAuthService.login(request, servletRequest, response));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<AuthResponse> bindPhone(@Valid @RequestBody MiniBindPhoneRequest request,
                                               HttpServletRequest servletRequest,
                                               HttpServletResponse response) {
        rateLimiter.check("mini-bind:" + Hashing.sha256(request.phone()) + ":"
                + servletRequest.getRemoteAddr(), BIND_LIMIT, WINDOW);
        return ApiResponse.ok(miniAuthService.bindPhone(request, servletRequest, response));
    }
}
