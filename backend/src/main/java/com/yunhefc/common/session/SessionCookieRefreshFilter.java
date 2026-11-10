package com.yunhefc.common.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 每个已登录请求重写会话 Cookie 的 Max-Age，实现 C 端 7 天 / B 端 2 小时滑动过期。
 * 登录、注册、微信回调、绑定手机与登出会轮换或销毁会话，由 Spring Session 自行写入 Cookie。
 */
public class SessionCookieRefreshFilter extends OncePerRequestFilter {

    private static final Set<String> SESSION_MUTATING_REQUESTS = Set.of(
            "POST /api/v1/c/auth/login",
            "POST /api/v1/c/auth/register",
            "POST /api/v1/c/auth/logout",
            "POST /api/v1/b/auth/login",
            "POST /api/v1/b/auth/logout",
            "POST /api/v1/c/auth/wechat/bind-phone",
            "GET /api/v1/c/auth/wechat/callback");

    private final HttpSessionIdResolver sessionIdResolver;

    public SessionCookieRefreshFilter(HttpSessionIdResolver sessionIdResolver) {
        this.sessionIdResolver = sessionIdResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && !SESSION_MUTATING_REQUESTS.contains(request.getMethod() + " " + request.getRequestURI())) {
            sessionIdResolver.setSessionId(request, response, session.getId());
        }
        chain.doFilter(request, response);
    }
}
