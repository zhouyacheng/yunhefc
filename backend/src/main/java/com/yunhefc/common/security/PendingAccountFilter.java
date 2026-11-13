package com.yunhefc.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.contracts.security.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 待审核账号只放行 05 §2.4 白名单中的公开读与本人自助写接口，其余请求返回 ACCOUNT_PENDING。
 */
public class PendingAccountFilter extends OncePerRequestFilter {

    private static final int PENDING = 0;
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private record AllowedRoute(String method, String pattern) {
    }

    private static final List<AllowedRoute> ALLOWED_READS = List.of(
            new AllowedRoute("GET", "/api/v1/c/auth/csrf"),
            new AllowedRoute("GET", "/api/v1/c/auth/wechat/authorize"),
            new AllowedRoute("GET", "/api/v1/c/auth/wechat/callback"),
            new AllowedRoute("GET", "/api/v1/c/auth/wechat/mock-authorize"),
            new AllowedRoute("GET", "/api/v1/b/auth/csrf"),
            new AllowedRoute("GET", "/api/v1/c/invites/*"),
            new AllowedRoute("GET", "/api/v1/c/team/players"),
            new AllowedRoute("GET", "/api/v1/c/team/stats"),
            new AllowedRoute("GET", "/api/v1/c/matches"),
            new AllowedRoute("GET", "/api/v1/c/matches/*"),
            new AllowedRoute("GET", "/api/v1/c/matches/*/events"),
            new AllowedRoute("GET", "/api/v1/c/me"),
            new AllowedRoute("GET", "/api/v1/c/notifications"),
            new AllowedRoute("GET", "/api/v1/c/notifications/unread-count"));

    private static final List<AllowedRoute> ALLOWED_WRITES = List.of(
            new AllowedRoute("POST", "/api/v1/c/auth/sms-codes"),
            new AllowedRoute("POST", "/api/v1/c/auth/register"),
            new AllowedRoute("POST", "/api/v1/c/auth/login"),
            new AllowedRoute("POST", "/api/v1/c/auth/logout"),
            new AllowedRoute("POST", "/api/v1/c/auth/password/reset"),
            new AllowedRoute("POST", "/api/v1/c/auth/wechat/bind-phone"),
            new AllowedRoute("POST", "/api/v1/c/invites/*/accept"),
            new AllowedRoute("POST", "/api/v1/c/files/avatar"),
            new AllowedRoute("POST", "/api/v1/c/notifications/*/read"),
            new AllowedRoute("POST", "/api/v1/c/notifications/read-all"),
            new AllowedRoute("PATCH", "/api/v1/c/me/player"));

    private final ObjectMapper objectMapper;

    public PendingAccountFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthPrincipal principal
                && principal.status() == PENDING
                && !isAllowed(request)) {
            response.setStatus(ErrorCode.ACCOUNT_PENDING.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(ErrorCode.ACCOUNT_PENDING.code(),
                            ErrorCode.ACCOUNT_PENDING.message(), null));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) {
            return true;
        }
        String path = request.getRequestURI();
        return matches(ALLOWED_READS, method, path) || matches(ALLOWED_WRITES, method, path);
    }

    private boolean matches(List<AllowedRoute> routes, String method, String path) {
        String effectiveMethod = "HEAD".equals(method) ? "GET" : method;
        return routes.stream()
                .anyMatch(route -> route.method().equals(effectiveMethod) && MATCHER.match(route.pattern(), path));
    }
}
