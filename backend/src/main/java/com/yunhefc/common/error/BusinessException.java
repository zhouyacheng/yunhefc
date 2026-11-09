package com.yunhefc.common.error;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object data() {
        return data;
    }
}
