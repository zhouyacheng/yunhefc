package com.yunhefc.auth.controller;

import com.yunhefc.auth.application.AuthService;
import com.yunhefc.auth.dto.AuthDtos.AuthResponse;
import com.yunhefc.auth.dto.AuthDtos.AuthResult;
import com.yunhefc.auth.dto.AuthDtos.BMeView;
import com.yunhefc.auth.dto.AuthDtos.LoginRequest;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.ratelimit.RedisRateLimiter;
import com.yunhefc.common.security.AuthSessionService;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/b")
public class BackendAuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;
    private final RedisRateLimiter rateLimiter;

    public BackendAuthController(AuthService authService, AuthSessionService authSessionService,
                                 RedisRateLimiter rateLimiter) {
        this.authService = authService;
        this.authSessionService = authSessionService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/auth/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) {
        return ApiResponse.ok(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest servletRequest,
                                           HttpServletResponse response) {
        rateLimiter.check("login:" + request.phone() + ":" + servletRequest.getRemoteAddr(), 10, Duration.ofMinutes(1));
        AuthResult result = authService.login(request, true);
        String csrfToken = authSessionService.establish(
                servletRequest, response, SessionKeys.B_USER_ID, result.user().id());
        return ApiResponse.ok(new AuthResponse(result.user(), result.player(), result.permissions(), csrfToken));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authSessionService.logout(request);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority('match:view:any','role:view:any','attendance:view:any','event:edit:any','lineup:view:any','grouping:view:any','player:view:any','settings:view:any','audit:view:any','invite:manage:any')")
    public ApiResponse<BMeView> me() {
        AuthPrincipal principal = CurrentUser.principal();
        AuthResult result = authService.meResult(principal.userId());
        return ApiResponse.ok(new BMeView(result.user(), result.permissions(), authService.sections(result.permissions())));
    }
}
