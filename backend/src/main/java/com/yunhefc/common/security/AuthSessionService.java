package com.yunhefc.common.security;

import com.yunhefc.contracts.security.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class AuthSessionService {

    private final CsrfTokenRepository csrfTokenRepository;
    private final Map<String, Duration> ttlByKey;

    public AuthSessionService(
            CsrfTokenRepository csrfTokenRepository,
            @Value("${yunhefc.session.c-ttl:7d}") Duration cTtl,
            @Value("${yunhefc.session.b-ttl:2h}") Duration bTtl) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.ttlByKey = Map.of(SessionKeys.C_USER_ID, cTtl, SessionKeys.B_USER_ID, bTtl);
    }

    public String establish(HttpServletRequest request, HttpServletResponse response,
                            String sessionKey, Long userId) {
        HttpSession session = request.getSession(true);
        session.setAttribute(sessionKey, userId);
        session.setMaxInactiveInterval((int) ttlByKey.getOrDefault(sessionKey, Duration.ofHours(2)).getSeconds());
        try {
            request.changeSessionId();
        } catch (IllegalStateException ignored) {
            // session already invalidated
        }
        return rotateCsrf(request, response);
    }

    public String rotateCsrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
        return token.getToken();
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
