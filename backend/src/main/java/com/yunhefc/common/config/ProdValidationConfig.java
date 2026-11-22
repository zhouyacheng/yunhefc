package com.yunhefc.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 仅在 prod profile 注册启动自检；dev/test 不受影响。
 */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
@EnableConfigurationProperties({SessionProperties.class, CorsProperties.class})
public class ProdValidationConfig {

    @Bean
    ProdStartupValidator prodStartupValidator(Environment environment, SessionProperties sessionProperties,
                                              CorsProperties corsProperties) {
        ProdStartupValidator validator = new ProdStartupValidator(environment, sessionProperties, corsProperties);
        validator.validate();
        return validator;
    }

    /** 启动后比对 Flyway 版本；库落后时以启动失败阻断发布（ADR 0002）。 */
    @Bean
    SchemaVersionGuard schemaVersionGuard(JdbcTemplate jdbcTemplate) {
        return new SchemaVersionGuard(jdbcTemplate);
    }
}
