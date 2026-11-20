package com.yunhefc.auth.miniprogram;

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
@EnableConfigurationProperties(MiniProgramProperties.class)
public class MiniProgramConfig {

    @Bean
    MiniProgramClient miniProgramClient(MiniProgramProperties properties, ObjectMapper objectMapper,
                                        Environment environment) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod && properties.isEnabled() && !properties.isReal()) {
            throw new IllegalStateException("生产环境 WECHAT_MINI_MODE 必须为 real");
        }
        if (prod && properties.isEnabled() && !properties.hasCredentials()) {
            throw new IllegalStateException(
                    "生产环境微信小程序 real 模式缺少 WECHAT_MINI_APP_ID/WECHAT_MINI_APP_SECRET");
        }
        if (prod && properties.isEnabled() && !properties.isAppIdFormatValid()) {
            throw new IllegalStateException(
                    "生产环境 WECHAT_MINI_APP_ID 格式非法：必须为 wx 开头的 18 位小程序 AppID（32 位十六进制通常是 AppSecret）");
        }
        if (properties.isReal()) {
            return new RealMiniProgramClient(properties, objectMapper, miniRestClient(properties));
        }
        return new MockMiniProgramClient();
    }

    private RestClient miniRestClient(MiniProgramProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
