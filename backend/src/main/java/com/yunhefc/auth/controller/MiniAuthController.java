package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.MiniAuthService;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.MiniDtos.MiniBindPhoneRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginRequest;
import com.yunhefc.auth.dto.MiniDtos.MiniLoginResponse;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.ratelimit.RedisRateLimiter;
import com.yunhefc.common.security.Hashing;
import com.yunhefc.common.util.MaskUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/c/auth/mini")
@RequiredArgsConstructor
public class MiniAuthController {

    private static final Logger log = LoggerFactory.getLogger(MiniAuthController.class);
    private static final int LOGIN_LIMIT = 30;
    private static final int BIND_LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MiniAuthService miniAuthService;
    private final RedisRateLimiter rateLimiter;

    @PostMapping("/login")
    public ApiResponse<MiniLoginResponse> login(@Valid @RequestBody MiniLoginRequest request,
                                                HttpServletRequest servletRequest,
                                                HttpServletResponse response) {
        checkRateLimit("mini-login:" + servletRequest.getRemoteAddr(), LOGIN_LIMIT,
                "mini login", servletRequest);
        return ApiResponse.ok(miniAuthService.login(request, servletRequest, response));
    }

    @PostMapping("/bind-phone")
    public ApiResponse<AuthResponse> bindPhone(@Valid @RequestBody MiniBindPhoneRequest request,
                                               HttpServletRequest servletRequest,
                                               HttpServletResponse response) {
        checkRateLimit("mini-bind:" + Hashing.sha256(request.phone()) + ":"
                + servletRequest.getRemoteAddr(), BIND_LIMIT, "mini bind phone", servletRequest);
        return ApiResponse.ok(miniAuthService.bindPhone(request, servletRequest, response));
    }

    private void checkRateLimit(String key, int limit, String event, HttpServletRequest servletRequest) {
        try {
            rateLimiter.check(key, limit, WINDOW);
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.RATE_LIMITED) {
                log.warn("{} rate limited, remoteIp={}", event,
                        MaskUtils.maskIp(servletRequest.getRemoteAddr()));
            }
            throw ex;
        }
    }
}
