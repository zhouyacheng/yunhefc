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
            "生产环境 CORS_ALLOWED_ORIGINS 必须恰好是同一域名的 https://www.<domain> 与 https://admin.<domain>"
                    + "（拒绝 localhost 与通配符）";
    private static final String PUBLIC_BASE_URL_RULE =
            "生产环境 PUBLIC_BASE_URL 必须与 CORS 的 https://www.<domain> 完全一致（不带路径与末尾斜杠）";

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
        validateCors();
        validatePublicBaseUrl();
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

    private void validateCors() {
        List<String> origins = corsProperties.allowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
        if (origins.size() != 2) {
            throw new IllegalStateException(CORS_RULE);
        }
        String wwwDomain = originDomain(origins, "www.");
        String adminDomain = originDomain(origins, "admin.");
        if (wwwDomain == null || adminDomain == null || !wwwDomain.equals(adminDomain)) {
            throw new IllegalStateException(CORS_RULE);
        }
    }

    private String originDomain(List<String> origins, String prefix) {
        for (String origin : origins) {
            String host = httpsHost(origin);
            if (host.startsWith(prefix) && host.length() > prefix.length()) {
                return host.substring(prefix.length());
            }
        }
        return null;
    }

    private String httpsHost(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(CORS_RULE);
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
            throw new IllegalStateException(CORS_RULE);
        }
        return host.toLowerCase(Locale.ROOT);
    }

    /** 邀请链接必须落在 C 端 www 源；同时拒绝首尾空白，避免校验值与实际拼接值不一致。 */
    private void validatePublicBaseUrl() {
        String value = environment.getProperty("yunhefc.public-base-url");
        if (!StringUtils.hasText(value) || !value.equals(value.trim()) || !value.equals(wwwOrigin())) {
            throw new IllegalStateException(PUBLIC_BASE_URL_RULE);
        }
    }

    private String wwwOrigin() {
        return corsProperties.allowedOrigins().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(origin -> httpsHost(origin).startsWith("www."))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(PUBLIC_BASE_URL_RULE));
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
