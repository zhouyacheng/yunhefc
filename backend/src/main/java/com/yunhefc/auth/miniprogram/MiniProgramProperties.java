package com.yunhefc.auth.miniprogram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "yunhefc.wechat.mini")
public class MiniProgramProperties {

    /** 小程序 AppID 固定为 wx + 16 位字符；32 位十六进制通常是 AppSecret。 */
    private static final Pattern APP_ID_PATTERN = Pattern.compile("^wx[0-9a-zA-Z]{16}$");

    private boolean enabled = false;
    private String mode = "mock";
    private String appId = "";
    private String appSecret = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isReal() {
        return "real".equalsIgnoreCase(mode);
    }

    public boolean hasCredentials() {
        return notBlank(appId) && notBlank(appSecret);
    }

    public boolean isAppIdFormatValid() {
        return appId != null && APP_ID_PATTERN.matcher(appId).matches();
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
