package com.yunhefc.auth.miniprogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MiniProgramConfigTest {

    private final MiniProgramConfig config = new MiniProgramConfig();

    @Test
    void prodRejectsEnabledMockMode() {
        MiniProgramProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("mock");

        assertThatThrownBy(() -> config.miniProgramClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WECHAT_MINI_MODE");
    }

    @Test
    void prodRejectsRealModeWithoutCredentials() {
        MiniProgramProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("real");
        properties.setAppId("");

        assertThatThrownBy(() -> config.miniProgramClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WECHAT_MINI_APP_ID");
    }

    @Test
    void prodRejectsInvalidAppIdFormat() {
        MiniProgramProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("real");
        properties.setAppId("2e65b1bb3d054466b82f0c9d125465e2");

        assertThatThrownBy(() -> config.miniProgramClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WECHAT_MINI_APP_ID");
    }

    @Test
    void prodWithRealCredentialsUsesRealClient() {
        MiniProgramProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("real");

        assertThat(config.miniProgramClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(RealMiniProgramClient.class);
    }

    @Test
    void prodDisabledMiniProgramDoesNotFail() {
        MiniProgramProperties properties = properties();
        properties.setEnabled(false);

        assertThat(config.miniProgramClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(MockMiniProgramClient.class);
    }

    @Test
    void nonProdMockModeUsesMockClient() {
        MiniProgramProperties properties = properties();
        properties.setMode("mock");

        assertThat(config.miniProgramClient(properties, new ObjectMapper(), env("test")))
                .isInstanceOf(MockMiniProgramClient.class);
    }

    private MiniProgramProperties properties() {
        MiniProgramProperties properties = new MiniProgramProperties();
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("app-secret");
        return properties;
    }

    private MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
