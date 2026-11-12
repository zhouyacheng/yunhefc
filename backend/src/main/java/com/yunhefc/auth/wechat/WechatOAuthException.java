package com.yunhefc.auth.wechat;

public final class WechatOAuthException extends RuntimeException {

    public enum Reason {
        INVALID_CODE,
        PROVIDER_ERROR
    }

    private final Reason reason;

    public WechatOAuthException(Reason reason) {
        super("wechat oauth failed: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
