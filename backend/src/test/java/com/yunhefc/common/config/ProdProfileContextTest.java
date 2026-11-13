package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProdProfileContextTest {

    @Test
    void startsWithCompleteProdConfiguration() {
        runner("true", "", "https://www.example.com,https://admin.example.com",
                "strong-password", "redis-password")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProdStartupValidator.class);
                });
    }

    @Test
    void failsFastWithInsecureProdConfiguration() {
        runner("false", "", "https://www.example.com,https://admin.example.com",
                "strong-password", "redis-password")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("生产环境 SESSION_COOKIE_SECURE 必须为 true");
                });
    }

    @Test
    void devProfileIsUnaffectedByProdValidation() {
        runner("false", ".example.com", "http://localhost:5273,http://localhost:5274",
                "yunhefc", "")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProdStartupValidator.class);
                });
    }

    private ApplicationContextRunner runner(String cookieSecure, String cookieDomain, String corsOrigins,
                                            String dbPassword, String redisPassword) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProdValidationConfig.class)
                .withPropertyValues(
                        "yunhefc.session.cookie-secure=" + cookieSecure,
                        "yunhefc.session.cookie-domain=" + cookieDomain,
                        "yunhefc.cors.allowed-origins=" + corsOrigins,
                        "yunhefc.public-base-url=https://www.example.com",
                        "spring.datasource.password=" + dbPassword,
                        "spring.data.redis.password=" + redisPassword);
    }
}
