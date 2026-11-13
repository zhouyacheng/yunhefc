package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

class ProdProfileYamlTest {

    @Test
    void bindsProductionProfileKeys() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SESSION_COOKIE_SECURE", "true")
                .withProperty("CORS_ALLOWED_ORIGINS",
                        "https://www.example.com,https://admin.example.com");
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))) {
            environment.getPropertySources().addLast(source);
        }

        Binder binder = Binder.get(environment);
        ServerProperties server = binder.bind("server", ServerProperties.class).get();
        assertThat(server.getForwardHeadersStrategy())
                .isEqualTo(ServerProperties.ForwardHeadersStrategy.FRAMEWORK);
        assertThat(server.getShutdown()).isEqualTo(Shutdown.GRACEFUL);
        assertThat(server.getError().isIncludeException()).isFalse();
        assertThat(server.getError().getIncludeStacktrace()).isEqualTo(ErrorProperties.IncludeAttribute.NEVER);
        assertThat(server.getError().getIncludeMessage()).isEqualTo(ErrorProperties.IncludeAttribute.NEVER);
        assertThat(server.getError().getIncludeBindingErrors()).isEqualTo(ErrorProperties.IncludeAttribute.NEVER);

        SessionProperties session = binder.bind("yunhefc.session", SessionProperties.class).get();
        assertThat(session.cookieSecure()).isTrue();
        assertThat(session.cookieDomain()).isEmpty();
        assertThat(session.cTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(session.bTtl()).isEqualTo(Duration.ofHours(2));

        CorsProperties cors = binder.bind("yunhefc.cors", CorsProperties.class).get();
        assertThat(cors.allowedOrigins())
                .containsExactly("https://www.example.com", "https://admin.example.com");

        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class))
                .isEqualTo(10);
        assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout", Integer.class))
                .isEqualTo(5000);
        assertThat(environment.getProperty("spring.datasource.hikari.max-lifetime", Integer.class))
                .isEqualTo(1800000);
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("redis");
        assertThat(environment.getProperty("spring.kafka.bootstrap-servers")).isEqualTo("kafka:9092");
        assertThat(environment.getProperty("spring.lifecycle.timeout-per-shutdown-phase")).isEqualTo("30s");
        assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
                .isEqualTo("readinessState,db,redis");
        assertThat(environment.getProperty("logging.structured.json.context.include", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
    }
}
