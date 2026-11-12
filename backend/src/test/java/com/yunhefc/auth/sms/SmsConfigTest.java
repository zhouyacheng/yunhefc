package com.yunhefc.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.config.SmsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SmsConfigTest {

    private final SmsConfig config = new SmsConfig();

    @Test
    void prodRejectsMockEnabled() {
        SmsProperties properties = properties();
        properties.setMockEnabled(true);

        assertThatThrownBy(() -> config.smsSender(properties, env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_MOCK_ENABLED");
    }

    @Test
    void prodRejectsMissingAppCode() {
        SmsProperties properties = properties();
        properties.setAppCode("");

        assertThatThrownBy(() -> config.smsSender(properties, env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_APPCODE");
    }

    @Test
    void prodRejectsMissingTemplateId() {
        SmsProperties properties = properties();
        properties.setTemplateId("");

        assertThatThrownBy(() -> config.smsSender(properties, env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_TEMPLATE_ID");
    }

    @Test
    void prodRejectsMissingSignId() {
        SmsProperties properties = properties();
        properties.setSignId("");

        assertThatThrownBy(() -> config.smsSender(properties, env("prod")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_SIGN_ID");
    }

    @Test
    void prodWithCredentialsUsesMarketplaceSender() {
        assertThat(config.smsSender(properties(), env("prod")))
                .isInstanceOf(MarketplaceSmsSender.class);
    }

    @Test
    void mockModeOutsideProdStartsWithoutCredentials() {
        SmsProperties properties = properties();
        properties.setMockEnabled(true);
        properties.setAppCode("");
        properties.setTemplateId("");
        properties.setSignId("");

        assertThat(config.smsSender(properties, env("test"))).isNotNull();
    }

    @Test
    void realModeWithoutCredentialsFailsOutsideProdToo() {
        SmsProperties properties = properties();
        properties.setAppCode("");

        assertThatThrownBy(() -> config.smsSender(properties, env("test")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMS_APPCODE");
    }

    private SmsProperties properties() {
        SmsProperties properties = new SmsProperties();
        properties.setAppCode("app-code");
        properties.setTemplateId("template-id");
        properties.setSignId("sign-id");
        properties.setTtlSeconds(300);
        return properties;
    }

    private MockEnvironment env(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
