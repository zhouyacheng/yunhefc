package com.yunhefc.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class OssConfigTest {

    private final OssConfig config = new OssConfig();

    @Test
    void prodRejectsMockMode() {
        OssProperties properties = new OssProperties();
        properties.setMode("mock");

        assertThatThrownBy(() -> config.ossGateway(properties, env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OSS_MODE");
    }

    @Test
    void prodRejectsRealModeWithoutCredentials() {
        OssProperties properties = new OssProperties();
        properties.setMode("real");

        assertThatThrownBy(() -> config.ossGateway(properties, env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少");
    }

    @Test
    void realModeWithoutCredentialsFailsOutsideProdToo() {
        OssProperties properties = new OssProperties();
        properties.setMode("real");

        assertThatThrownBy(() -> config.ossGateway(properties, env("dev")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mockModeUsesInMemoryGateway() {
        OssProperties properties = new OssProperties();

        assertThat(config.ossGateway(properties, env("test"))).isInstanceOf(InMemoryOssGateway.class);
    }

    @Test
    void realModeWithCredentialsUsesAliyunGateway() {
        OssProperties properties = new OssProperties();
        properties.setMode("real");
        properties.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("yunhefc-test");
        properties.setAccessKeyId("access-key");
        properties.setAccessKeySecret("access-secret");

        OssGateway gateway = config.ossGateway(properties, env("prod"));
        assertThat(gateway).isInstanceOf(AliyunOssGateway.class);
        ((AliyunOssGateway) gateway).shutdown();
    }

    private MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
