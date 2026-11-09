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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

public class PendingAccountFilter extends OncePerRequestFilter {

    private static final int PENDING = 0;
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final Set<String> ALLOWED_WRITE_PATHS = Set.of(
            "/api/v1/c/auth/login",
            "/api/v1/c/auth/logout",
            "/api/v1/c/auth/sms-codes",
            "/api/v1/c/auth/register",
            "/api/v1/c/auth/password/reset",
            "/api/v1/c/auth/wechat/bind-phone"
    );

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
                && !SAFE_METHODS.contains(request.getMethod())
                && !isAllowedWrite(request)) {
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

    private boolean isAllowedWrite(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (ALLOWED_WRITE_PATHS.contains(uri)) {
            return true;
        }
        return uri.startsWith("/api/v1/c/invites/") && uri.endsWith("/accept");
    }
}
