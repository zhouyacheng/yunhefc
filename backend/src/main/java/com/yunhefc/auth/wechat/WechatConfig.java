package com.yunhefc.auth.wechat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatConfig {

    @Bean
    WechatOAuthClient wechatOAuthClient(WechatProperties properties, ObjectMapper objectMapper) {
        if ("real".equalsIgnoreCase(properties.getMode())) {
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
