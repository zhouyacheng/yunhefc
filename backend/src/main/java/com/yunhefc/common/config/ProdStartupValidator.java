package com.yunhefc.common.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 生产启动自检：Cookie、CORS 与 DB/Redis 口令不安全时立即失败。
 */
public class ProdStartupValidator {

    private static final String DEFAULT_DB_PASSWORD = "yunhefc";
    private static final String CORS_RULE =
            "生产环境 CORS_ALLOWED_ORIGINS 必须恰为一个与 PUBLIC_BASE_URL 相同的 https://www.<domain> 单源"
                    + "（拒绝空值、多源、localhost、通配符、admin./api. 子域）";
    private static final String PUBLIC_BASE_URL_RULE =
            "生产环境 PUBLIC_BASE_URL 必须为 https://www.<domain>（不带端口、路径与末尾斜杠）";

    private final Environment environment;
    private final SessionProperties sessionProperties;
    private final CorsProperties corsProperties;

    public ProdStartupValidator(Environment environment, SessionProperties sessionProperties,
                                CorsProperties corsProperties) {
        this.environment = environment;
        this.sessionProperties = sessionProperties;
        this.corsProperties = corsProperties;
    }

    public void validate() {
        validateSessionCookie();
        String publicBaseUrl = validatePublicBaseUrl();
        validateCors(publicBaseUrl);
        validateDbPassword();
        validateRedisPassword();
    }

    private void validateSessionCookie() {
        if (!sessionProperties.cookieSecure()) {
            throw new IllegalStateException("生产环境 SESSION_COOKIE_SECURE 必须为 true");
        }
        if (StringUtils.hasText(sessionProperties.cookieDomain())) {
            throw new IllegalStateException("生产环境 SESSION_COOKIE_DOMAIN 必须为空（Cookie 必须 host-only）");
        }
    }

    /** 同域收敛后 PUBLIC_BASE_URL 是唯一规范源；先校验它，再要求 CORS 与它完全一致。 */
    private String validatePublicBaseUrl() {
        String value = environment.getProperty("yunhefc.public-base-url");
        if (!StringUtils.hasText(value) || !value.equals(value.trim())) {
            throw new IllegalStateException(PUBLIC_BASE_URL_RULE);
        }
        String host = httpsHost(value, PUBLIC_BASE_URL_RULE);
        if (!host.startsWith("www.") || host.length() <= 4 || host.indexOf('.', 4) <= 4) {
            throw new IllegalStateException(PUBLIC_BASE_URL_RULE);
        }
        if (!value.equals("https://" + host)) {
            throw new IllegalStateException(PUBLIC_BASE_URL_RULE);
        }
        return value;
    }

    private void validateCors(String publicBaseUrl) {
        List<String> origins = corsProperties.allowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (origins.size() != 1) {
            throw new IllegalStateException(CORS_RULE);
        }
        String origin = origins.get(0);
        String host = httpsHost(origin, CORS_RULE);
        if (!origin.equals("https://" + host) || !origin.equals(publicBaseUrl)) {
            throw new IllegalStateException(CORS_RULE);
        }
    }

    private String httpsHost(String origin, String rule) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(rule);
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || uri.getPort() != -1
                || uri.getRawUserInfo() != null
                || StringUtils.hasText(uri.getPath())
                || uri.getQuery() != null
                || uri.getFragment() != null
                || host.contains("*")
                || host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")) {
            throw new IllegalStateException(rule);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private void validateDbPassword() {
        String password = environment.getProperty("spring.datasource.password");
        if (!StringUtils.hasText(password) || DEFAULT_DB_PASSWORD.equals(password.trim())) {
            throw new IllegalStateException("生产环境 DB_PASSWORD 不得为空或使用默认值 yunhefc");
        }
    }

    private void validateRedisPassword() {
        if (!StringUtils.hasText(environment.getProperty("spring.data.redis.password"))) {
            throw new IllegalStateException("生产环境 REDIS_PASSWORD 不得为空");
        }
    }
}
