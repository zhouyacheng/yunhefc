package com.yunhefc.common.security;

import com.yunhefc.common.config.SessionProperties;
import com.yunhefc.contracts.security.SessionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class AuthSessionService {

    private final CsrfTokenRepository csrfTokenRepository;
    private final Map<String, Duration> ttlByKey;

    public AuthSessionService(CsrfTokenRepository csrfTokenRepository, SessionProperties properties) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.ttlByKey = Map.of(SessionKeys.C_USER_ID, properties.cTtl(), SessionKeys.B_USER_ID, properties.bTtl());
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
