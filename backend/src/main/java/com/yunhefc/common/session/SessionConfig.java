package com.yunhefc.common.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.time.Duration;

@Configuration
public class SessionConfig {

    @Bean
    HttpSessionIdResolver httpSessionIdResolver(
            @Value("${yunhefc.session.c-ttl:7d}") Duration cTtl,
            @Value("${yunhefc.session.b-ttl:2h}") Duration bTtl,
            @Value("${yunhefc.session.cookie-domain:}") String cookieDomain,
            @Value("${yunhefc.session.cookie-secure:false}") boolean cookieSecure,
            @Value("${yunhefc.session.same-site:Lax}") String sameSite) {
        return new PathSessionIdResolver(cTtl, bTtl, cookieDomain, cookieSecure, sameSite);
    }
}
