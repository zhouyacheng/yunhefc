package com.yunhefc.common.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.session.web.http.HttpSessionIdResolver;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.List;

public class PathSessionIdResolver implements HttpSessionIdResolver {

    private static final String C_COOKIE = "YHFC_C";
    private static final String B_COOKIE = "YHFC_B";
    private static final String C_PATH = "/api/v1/c";
    private static final String B_PATH = "/api/v1/b";

    private final Duration cTtl;
    private final Duration bTtl;
    private final String cookieDomain;
    private final boolean cookieSecure;
    private final String sameSite;

    public PathSessionIdResolver(Duration cTtl, Duration bTtl, String cookieDomain,
                                 boolean cookieSecure, String sameSite) {
        this.cTtl = cTtl;
        this.bTtl = bTtl;
        this.cookieDomain = cookieDomain;
        this.cookieSecure = cookieSecure;
        this.sameSite = sameSite;
    }

    @Override
    public List<String> resolveSessionIds(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookieName(request));
        return cookie == null ? List.of() : List.of(cookie.getValue());
    }

    @Override
    public void setSessionId(HttpServletRequest request, HttpServletResponse response, String sessionId) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, sessionId, ttl(request)).toString());
    }

    @Override
    public void expireSession(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(request, "", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(HttpServletRequest request, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(cookieName(request), value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path(path(request))
                .maxAge(maxAge);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }

    private String cookieName(HttpServletRequest request) {
        return request.getRequestURI().startsWith(B_PATH) ? B_COOKIE : C_COOKIE;
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().startsWith(B_PATH) ? B_PATH : C_PATH;
    }

    private Duration ttl(HttpServletRequest request) {
        return request.getRequestURI().startsWith(B_PATH) ? bTtl : cTtl;
    }
}
