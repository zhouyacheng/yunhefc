package com.yunhefc.auth.sms;

public final class SmsSendException extends RuntimeException {

    public enum Reason {
        THROTTLED,
        PROVIDER_ERROR
    }

    private final Reason reason;
    private final Integer retryAfterSeconds;

    public SmsSendException(Reason reason) {
        this(reason, null);
    }

    public SmsSendException(Reason reason, Integer retryAfterSeconds) {
        super("sms send failed: " + reason);
        this.reason = reason;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Reason reason() {
        return reason;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
