package com.yunhefc.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhefc.common.api.ApiResponse;
import com.yunhefc.common.api.RequestIdFilter;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.PendingAccountFilter;
import com.yunhefc.common.security.SessionAuthFilter;
import com.yunhefc.common.session.SessionCookieRefreshFilter;
import com.yunhefc.contracts.security.UserAccessPort;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.SessionManagementFilter;
import org.springframework.session.web.http.HttpSessionIdResolver;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserAccessPort userAccessPort;
    private final ObjectMapper objectMapper;
    private final HttpSessionIdResolver sessionIdResolver;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName("_csrf");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers("/actuator/**"))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.none()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/c/auth/csrf", "/api/v1/b/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/c/auth/wechat/authorize",
                                "/api/v1/c/auth/wechat/callback").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/c/auth/sms-codes",
                                "/api/v1/c/auth/register",
                                "/api/v1/c/auth/login",
                                "/api/v1/c/auth/password/reset",
                                "/api/v1/c/auth/wechat/bind-phone",
                                "/api/v1/b/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/c/invites/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/c/team/players",
                                "/api/v1/c/team/stats",
                                "/api/v1/c/matches",
                                "/api/v1/c/matches/*").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(new RequestIdFilter(), CsrfFilter.class)
                // 认证必须晚于 SessionManagementFilter：否则首个已登录请求会被判为“请求内新认证”，
                // 触发 CsrfAuthenticationStrategy 轮换 CSRF token，使登录响应返回的 token 失效。
                .addFilterAfter(new SessionAuthFilter(userAccessPort), SessionManagementFilter.class)
                .addFilterAfter(new SessionCookieRefreshFilter(sessionIdResolver), SessionAuthFilter.class)
                .addFilterAfter(new PendingAccountFilter(objectMapper), SessionCookieRefreshFilter.class);

        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(errorCode.code(), errorCode.message(), null));
    }
}
