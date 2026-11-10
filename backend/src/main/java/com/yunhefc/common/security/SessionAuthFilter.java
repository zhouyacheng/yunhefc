package com.yunhefc.common.security;

import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.SessionKeys;
import com.yunhefc.contracts.security.UserAccessPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SessionAuthFilter extends OncePerRequestFilter {

    private final UserAccessPort userAccessPort;

    public SessionAuthFilter(UserAccessPort userAccessPort) {
        this.userAccessPort = userAccessPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String key = request.getRequestURI().startsWith("/api/v1/b/")
                    ? SessionKeys.B_USER_ID
                    : SessionKeys.C_USER_ID;
            Object value = session.getAttribute(key);
            if (value instanceof Long userId) {
                userAccessPort.load(userId).ifPresentOrElse(
                        principal -> authenticate(principal, request),
                        () -> {
                            // 账号已注销或不存在：清除会话中可能残留的 SecurityContext 并立即失效
                            SecurityContextHolder.clearContext();
                            session.invalidate();
                        });
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(AuthPrincipal principal, HttpServletRequest request) {
        var authorities = principal.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
