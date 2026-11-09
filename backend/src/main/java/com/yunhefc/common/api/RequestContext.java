package com.yunhefc.common.api;

public final class RequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setRequestId(String requestId) {
        REQUEST_ID.set(requestId);
    }

    public static void setClientIp(String clientIp) {
        CLIENT_IP.set(clientIp);
    }

    public static String requestId() {
        String requestId = REQUEST_ID.get();
        return requestId == null ? "" : requestId;
    }

    public static String clientIp() {
        return CLIENT_IP.get();
    }

    public static void clear() {
        REQUEST_ID.remove();
        CLIENT_IP.remove();
    }
}
