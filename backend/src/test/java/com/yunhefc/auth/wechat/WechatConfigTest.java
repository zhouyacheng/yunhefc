package com.yunhefc.auth.wechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WechatConfigTest {

    private final WechatConfig config = new WechatConfig();

    @Test
    void prodRejectsEnabledMockMode() {
        WechatProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("mock");

        assertThatThrownBy(() -> config.wechatOAuthClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WECHAT_MODE");
    }

    @Test
    void prodRejectsRealModeWithoutCredentials() {
        WechatProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("real");
        properties.setAppId("");

        assertThatThrownBy(() -> config.wechatOAuthClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WECHAT_APP_ID");
    }

    @Test
    void prodRejectsInvalidAppIdFormat() {
        WechatProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("real");
        properties.setAppId("2e65b1bb3d054466b82f0c9d125465e2");

        assertThatThrownBy(() -> config.wechatOAuthClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WECHAT_APP_ID");
    }

    @Test
    void prodWithRealCredentialsUsesRealClient() {
        WechatProperties properties = properties();
        properties.setEnabled(true);
        properties.setMode("real");

        assertThat(config.wechatOAuthClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(RealWechatOAuthClient.class);
    }

    @Test
    void prodDisabledWechatDoesNotFail() {
        WechatProperties properties = properties();
        properties.setEnabled(false);

        assertThat(config.wechatOAuthClient(properties, new ObjectMapper(), env("prod")))
                .isInstanceOf(MockWechatOAuthClient.class);
    }

    @Test
    void nonProdMockModeUsesMockClient() {
        WechatProperties properties = properties();
        properties.setMode("mock");

        assertThat(config.wechatOAuthClient(properties, new ObjectMapper(), env("test")))
                .isInstanceOf(MockWechatOAuthClient.class);
    }

    private WechatProperties properties() {
        WechatProperties properties = new WechatProperties();
        properties.setAppId("wx1234567890abcdef");
        properties.setAppSecret("app-secret");
        properties.setCallbackUrl("https://www.example.com/api/v1/c/auth/wechat/callback");
        return properties;
    }

    private MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
