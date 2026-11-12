package com.yunhefc.auth.sms;

import com.yunhefc.common.config.SmsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    @Bean
    SmsSender smsSender(SmsProperties properties, Environment environment) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod && properties.isMockEnabled()) {
            throw new IllegalStateException("生产环境 SMS_MOCK_ENABLED 必须为 false");
        }
        if (properties.isMockEnabled()) {
            return (phone, code) -> {
                // mock 模式下 SmsCodeService 直接写入固定验证码，不触发真实发送。
            };
        }
        return new MarketplaceSmsSender(properties);
    }
}
