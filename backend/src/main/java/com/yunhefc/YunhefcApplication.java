package com.yunhefc;

import com.yunhefc.auth.infrastructure.InviteProperties;
import com.yunhefc.common.config.AppProperties;
import com.yunhefc.common.config.CorsProperties;
import com.yunhefc.common.config.LoginRateLimitProperties;
import com.yunhefc.common.config.MessagingProperties;
import com.yunhefc.common.config.OutboxProperties;
import com.yunhefc.common.config.RetentionProperties;
import com.yunhefc.common.config.SessionProperties;
import com.yunhefc.common.config.SmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AppProperties.class,
        CorsProperties.class,
        LoginRateLimitProperties.class,
        MessagingProperties.class,
        OutboxProperties.class,
        RetentionProperties.class,
        SessionProperties.class,
        SmsProperties.class,
        InviteProperties.class
})
public class YunhefcApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunhefcApplication.class, args);
    }
}
