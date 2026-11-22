package com.yunhefc.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 短信 IP 维度限流（03 §5：10 次/小时/IP）；E2E 可经环境变量放宽。 */
@Validated
@ConfigurationProperties(prefix = "yunhefc.auth.sms-rate-limit")
public record SmsRateLimitProperties(
        @DefaultValue("10") @Min(1) int ipMaxPerHour) {
}
