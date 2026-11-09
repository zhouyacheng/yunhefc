package com.yunhefc.common.api;

public record ApiResponse<T>(int code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, RequestContext.requestId());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null, RequestContext.requestId());
    }

    public static ApiResponse<Object> error(int code, String message, Object data) {
        return new ApiResponse<>(code, message, data, RequestContext.requestId());
    }
}
