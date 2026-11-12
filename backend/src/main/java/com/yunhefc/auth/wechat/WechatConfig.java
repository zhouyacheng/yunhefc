package com.yunhefc.auth.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatConfig {

    @Bean
    WechatOAuthClient wechatOAuthClient(WechatProperties properties, ObjectMapper objectMapper,
                                        Environment environment) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod && properties.isEnabled() && !properties.isReal()) {
            throw new IllegalStateException("生产环境 WECHAT_MODE 必须为 real");
        }
        if (prod && properties.isEnabled() && !properties.hasCredentials()) {
            throw new IllegalStateException(
                    "生产环境微信 real 模式缺少 WECHAT_APP_ID/WECHAT_APP_SECRET/WECHAT_CALLBACK_URL");
        }
        if (properties.isReal()) {
            return new RealWechatOAuthClient(properties, objectMapper, wechatRestClient(properties));
        }
        return new MockWechatOAuthClient();
    }

    private RestClient wechatRestClient(WechatProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
