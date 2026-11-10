package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.AuthService;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.AuthDtos.AuthResult;
import com.yunhefc.auth.dto.AuthDtos.LoginRequest;
import com.yunhefc.auth.dto.AuthDtos.MeView;
import com.yunhefc.auth.dto.AuthDtos.RegisterRequest;
import com.yunhefc.auth.dto.AuthDtos.RegisterResult;
import com.yunhefc.auth.dto.AuthDtos.ResetPasswordRequest;
import com.yunhefc.auth.dto.AuthDtos.SessionResponse;
import com.yunhefc.auth.dto.AuthDtos.SmsCodeRequest;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.ratelimit.RedisRateLimiter;
import com.yunhefc.common.security.AuthSessionService;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.security.Hashing;
import com.yunhefc.contracts.security.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final RedisRateLimiter rateLimiter;

    @GetMapping("/auth/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @PostMapping("/auth/sms-codes")
    public ApiResponse<Map<String, Integer>> sms(@Valid @RequestBody SmsCodeRequest request) {
        rateLimiter.check("sms:" + Hashing.sha256(request.phone()), 5, Duration.ofMinutes(1));
        return ApiResponse.ok(Map.of("expiresIn", authService.sendSms(request.phone(), request.purpose())));
    }

    @PostMapping("/auth/register")
    public ApiResponse<SessionResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest servletRequest,
                                                 HttpServletResponse response) {
        RegisterResult result = authService.register(request);
        String csrfToken = authSessionService.establish(
                servletRequest, response, SessionKeys.C_USER_ID, result.user().id());
        return ApiResponse.ok(new SessionResponse(result.user(), result.player(), csrfToken));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest servletRequest,
                                           HttpServletResponse response) {
        rateLimiter.check("login:" + Hashing.sha256(request.phone()) + ":" + servletRequest.getRemoteAddr(),
                10, Duration.ofMinutes(1));
        AuthResult result = authService.login(request, false);
        return ApiResponse.ok(authResponse(result, SessionKeys.C_USER_ID, servletRequest, response));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authSessionService.logout(request);
        return ApiResponse.ok();
    }

    @PostMapping("/auth/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<MeView> me() {
        return ApiResponse.ok(authService.me(CurrentUser.principal().userId()));
    }

    private AuthResponse authResponse(AuthResult result, String sessionKey,
                                      HttpServletRequest request, HttpServletResponse response) {
        String csrfToken = authSessionService.establish(request, response, sessionKey, result.user().id());
        return new AuthResponse(result.user(), result.player(), result.permissions(), csrfToken);
    }
}
