package com.yunhefc.common.session;

import com.yunhefc.common.config.SessionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.HttpSessionIdResolver;

@Configuration
public class SessionConfig {

    @Bean
    HttpSessionIdResolver httpSessionIdResolver(SessionProperties properties) {
        return new PathSessionIdResolver(
                properties.cTtl(),
                properties.bTtl(),
                properties.cookieDomain(),
                properties.cookieSecure(),
                properties.sameSite());
    }
}
