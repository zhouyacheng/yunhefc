package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RetentionPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertyConfig.class);

    @Test
    void defaultsMatchRetentionPolicy() {
        runner.run(context -> {
            RetentionProperties properties = context.getBean(RetentionProperties.class);

            assertThat(properties.getNotificationDays()).isEqualTo(90);
            assertThat(properties.getInviteDays()).isEqualTo(30);
            assertThat(properties.getOutboxDays()).isEqualTo(7);
            assertThat(properties.getConsumedEventDays()).isEqualTo(7);
        });
    }

    @Test
    void bindsOverrides() {
        runner.withPropertyValues(
                        "yunhefc.retention.notification-days=120",
                        "yunhefc.retention.invite-days=45",
                        "yunhefc.retention.outbox-days=3",
                        "yunhefc.retention.consumed-event-days=4")
                .run(context -> {
                    RetentionProperties properties = context.getBean(RetentionProperties.class);

                    assertThat(properties.getNotificationDays()).isEqualTo(120);
                    assertThat(properties.getInviteDays()).isEqualTo(45);
                    assertThat(properties.getOutboxDays()).isEqualTo(3);
                    assertThat(properties.getConsumedEventDays()).isEqualTo(4);
                });
    }

    @Test
    void rejectsNonPositiveDays() {
        runner.withPropertyValues("yunhefc.retention.outbox-days=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("outboxDays");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RetentionProperties.class)
    static class PropertyConfig {
    }
}
