package com.yunhefc.auth.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "yunhefc.wechat")
public class WechatProperties {

    private boolean enabled = false;
    private String mode = "mock";
    private String appId = "";
    private String appSecret = "";
    private String callbackUrl = "";
    private List<String> allowedRedirectPrefixes = new ArrayList<>();
    private boolean requireWechatUa = true;
    private long stateTtlSeconds = 300;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isReal() {
        return "real".equalsIgnoreCase(mode);
    }

    public boolean hasCredentials() {
        return notBlank(appId) && notBlank(appSecret) && notBlank(callbackUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public List<String> getAllowedRedirectPrefixes() {
        return allowedRedirectPrefixes;
    }

    public void setAllowedRedirectPrefixes(List<String> allowedRedirectPrefixes) {
        this.allowedRedirectPrefixes = allowedRedirectPrefixes;
    }

    public boolean isRequireWechatUa() {
        return requireWechatUa;
    }

    public void setRequireWechatUa(boolean requireWechatUa) {
        this.requireWechatUa = requireWechatUa;
    }

    public long getStateTtlSeconds() {
        return stateTtlSeconds;
    }

    public void setStateTtlSeconds(long stateTtlSeconds) {
        this.stateTtlSeconds = stateTtlSeconds;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
