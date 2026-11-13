package com.yunhefc.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.util.List;

class ProdStartupValidatorTest {

    @Test
    void acceptsSecureProdConfiguration() {
        assertThatCode(() -> validator(session(true, ""),
                cors("https://www.example.com", "https://admin.example.com"),
                "strong-password", "redis-password").validate())
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsOriginsInAnyOrder() {
        assertThatCode(() -> validator(session(true, ""),
                cors("https://admin.example.com", "https://www.example.com"),
                "strong-password", "redis-password").validate())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInsecureCookie() {
        assertThatThrownBy(() -> validator(session(false, ""), corsOfExampleDomain(),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SESSION_COOKIE_SECURE");
    }

    @Test
    void rejectsCookieDomain() {
        assertThatThrownBy(() -> validator(session(true, ".example.com"), corsOfExampleDomain(),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SESSION_COOKIE_DOMAIN");
    }

    @Test
    void rejectsLocalhostCorsOrigin() {
        assertThatThrownBy(() -> validator(session(true, ""),
                cors("https://www.example.com", "http://localhost:5273"),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsWildcardCorsOrigin() {
        assertThatThrownBy(() -> validator(session(true, ""),
                cors("https://www.example.com", "https://*.example.com"),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsCorsOriginsFromDifferentDomains() {
        assertThatThrownBy(() -> validator(session(true, ""),
                cors("https://www.example.com", "https://admin.other.com"),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsIncompleteCorsOrigins() {
        assertThatThrownBy(() -> validator(session(true, ""),
                cors("https://www.example.com"),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsHttpCorsOrigins() {
        assertThatThrownBy(() -> validator(session(true, ""),
                cors("http://www.example.com", "http://admin.example.com"),
                "strong-password", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    void rejectsDefaultDbPassword() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "yunhefc", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void rejectsEmptyDbPassword() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "", "redis-password").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void rejectsEmptyRedisPassword() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    @Test
    void rejectsMissingPublicBaseUrl() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "redis-password", "").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }

    @Test
    void rejectsLocalhostPublicBaseUrl() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "redis-password", "http://localhost:5273").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }

    @Test
    void rejectsPublicBaseUrlWithPathOrTrailingSlash() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "redis-password", "https://www.example.com/").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "redis-password", "https://www.example.com/app").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }

    @Test
    void rejectsPublicBaseUrlThatIsNotTheWwwOrigin() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "redis-password", "https://admin.example.com").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }

    @Test
    void rejectsPaddedPublicBaseUrl() {
        assertThatThrownBy(() -> validator(session(true, ""), corsOfExampleDomain(),
                "strong-password", "redis-password", "https://www.example.com ").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PUBLIC_BASE_URL");
    }

    private ProdStartupValidator validator(SessionProperties session, CorsProperties cors,
                                           String dbPassword, String redisPassword) {
        return validator(session, cors, dbPassword, redisPassword, "https://www.example.com");
    }

    private ProdStartupValidator validator(SessionProperties session, CorsProperties cors,
                                           String dbPassword, String redisPassword,
                                           String publicBaseUrl) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", dbPassword)
                .withProperty("spring.data.redis.password", redisPassword)
                .withProperty("yunhefc.public-base-url", publicBaseUrl);
        return new ProdStartupValidator(environment, session, cors);
    }

    private SessionProperties session(boolean secure, String domain) {
        return new SessionProperties(Duration.ofDays(7), Duration.ofHours(2), domain, secure, "Lax");
    }

    private CorsProperties corsOfExampleDomain() {
        return cors("https://www.example.com", "https://admin.example.com");
    }

    private CorsProperties cors(String... origins) {
        return new CorsProperties(List.of(origins));
    }
}
