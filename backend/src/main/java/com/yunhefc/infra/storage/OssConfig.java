package com.yunhefc.infra.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    @Bean(destroyMethod = "shutdown")
    OssGateway ossGateway(OssProperties properties, Environment environment) {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        if (prod && !properties.isReal()) {
            throw new IllegalStateException("生产环境 OSS_MODE 必须为 real");
        }
        if (properties.isReal()) {
            if (!properties.hasCredentials()) {
                throw new IllegalStateException("OSS real 模式缺少 endpoint/bucket/access-key 配置");
            }
            return new AliyunOssGateway(properties);
        }
        return new InMemoryOssGateway();
    }
}
