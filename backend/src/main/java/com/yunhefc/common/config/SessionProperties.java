package com.yunhefc.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "yunhefc.session")
public record SessionProperties(
        @DefaultValue("7d") Duration cTtl,
        @DefaultValue("12h") Duration bTtl,
        @DefaultValue("60s") Duration handoffTtl,
        @DefaultValue("") String cookieDomain,
        @DefaultValue("false") boolean cookieSecure,
        @DefaultValue("Lax") String sameSite) {
}
