package com.yunhefc.auth.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatConfig {

    @Bean
    WechatOAuthClient wechatOAuthClient(WechatProperties properties, ObjectMapper objectMapper) {
        if ("real".equalsIgnoreCase(properties.getMode())) {
            return new RealWechatOAuthClient(properties, objectMapper);
        }
        return new MockWechatOAuthClient();
    }
}
