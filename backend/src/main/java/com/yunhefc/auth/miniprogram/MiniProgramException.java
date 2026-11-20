package com.yunhefc.auth.miniprogram;

public final class MiniProgramException extends RuntimeException {

    public enum Reason {
        INVALID_CODE,
        PROVIDER_ERROR
    }

    private final Reason reason;

    public MiniProgramException(Reason reason) {
        super("mini program login failed: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
