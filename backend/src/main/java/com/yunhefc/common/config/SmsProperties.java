package com.yunhefc.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yunhefc.sms")
public class SmsProperties {

    private String mockCode = "123456";
    private long ttlSeconds = 300;

    public String getMockCode() {
        return mockCode;
    }

    public void setMockCode(String mockCode) {
        this.mockCode = mockCode;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
