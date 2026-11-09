package com.yunhefc.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yunhefc.kafka")
public class MessagingProperties {

    private String auditTopic = "yunhefc.audit.events";
    private String notificationTopic = "yunhefc.notification.events";
    private boolean listenerEnabled = true;
    private Retry retry = new Retry();

    public String getAuditTopic() {
        return auditTopic;
    }

    public void setAuditTopic(String auditTopic) {
        this.auditTopic = auditTopic;
    }

    public String getNotificationTopic() {
        return notificationTopic;
    }

    public void setNotificationTopic(String notificationTopic) {
        this.notificationTopic = notificationTopic;
    }

    public boolean isListenerEnabled() {
        return listenerEnabled;
    }

    public void setListenerEnabled(boolean listenerEnabled) {
        this.listenerEnabled = listenerEnabled;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Retry {

        private long maxRetries = 3;
        private long intervalMs = 1000;

        public long getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(long maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }
}
