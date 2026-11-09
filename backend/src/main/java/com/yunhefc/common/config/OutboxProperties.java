package com.yunhefc.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yunhefc.outbox")
public class OutboxProperties {

    private boolean enabled = true;
    private long relayIntervalMs = 60000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getRelayIntervalMs() {
        return relayIntervalMs;
    }

    public void setRelayIntervalMs(long relayIntervalMs) {
        this.relayIntervalMs = relayIntervalMs;
    }
}
