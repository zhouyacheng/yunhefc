package com.yunhefc.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "yunhefc.auth.login-rate-limit")
public record LoginRateLimitProperties(
        @DefaultValue("10") @Min(1) int maxAttempts,
        @DefaultValue("1m") Duration window) {
}
