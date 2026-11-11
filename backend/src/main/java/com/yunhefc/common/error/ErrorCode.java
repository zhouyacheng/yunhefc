package com.yunhefc.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_FAILED(40001, HttpStatus.BAD_REQUEST, "参数/字段校验失败"),
    UNAUTHENTICATED(40101, HttpStatus.UNAUTHORIZED, "未登录或会话失效"),
    INVALID_CREDENTIALS(40102, HttpStatus.UNAUTHORIZED, "手机号或密码错误"),
    ACCOUNT_LOCKED(40103, HttpStatus.UNAUTHORIZED, "账号已锁定"),
    ACCOUNT_DISABLED(40104, HttpStatus.UNAUTHORIZED, "账号已注销"),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "无权限"),
    ADMIN_SECTION_FORBIDDEN(40302, HttpStatus.FORBIDDEN, "无后台分区权限"),
    ACCOUNT_PENDING(40303, HttpStatus.FORBIDDEN, "待审核账号不能执行该操作"),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(40501, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    VERSION_CONFLICT(40901, HttpStatus.CONFLICT, "版本冲突"),
    DUPLICATE_ACTIVE_REGISTRATION(40902, HttpStatus.CONFLICT, "已有有效报名/候补"),
    CAPACITY_FULL(40903, HttpStatus.CONFLICT, "名额与候补均已满"),
    DUPLICATE_JERSEY(40904, HttpStatus.CONFLICT, "正式球员号码冲突"),
    MATCH_STATE_CONFLICT(40905, HttpStatus.CONFLICT, "比赛状态不允许该操作"),
    IDEMPOTENCY_KEY_REUSED(40906, HttpStatus.CONFLICT, "同一幂等键对应不同请求"),
    PHONE_EXISTS(40907, HttpStatus.CONFLICT, "手机号已注册"),
    ROLE_IN_USE(40908, HttpStatus.CONFLICT, "角色仍被用户占用"),
    INVITE_UNAVAILABLE(40910, HttpStatus.CONFLICT, "邀请不可用"),
    GROUPING_FINGERPRINT_CONFLICT(40911, HttpStatus.CONFLICT, "分组名单指纹不一致，需重新生成"),
    ROLE_CODE_EXISTS(40912, HttpStatus.CONFLICT, "角色编码重复"),
    IDEMPOTENCY_IN_PROGRESS(40915, HttpStatus.CONFLICT, "幂等请求正在处理中"),
    LAST_ADMIN_PROTECTED(40913, HttpStatus.CONFLICT, "不能移除最后一个管理员"),
    DEFAULT_ROLE_PROTECTED(40914, HttpStatus.CONFLICT, "默认角色不可删除"),
    UNSUPPORTED_MEDIA_TYPE(41501, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求媒体类型不支持"),
    BUSINESS_RULE_VIOLATION(42201, HttpStatus.UNPROCESSABLE_ENTITY, "业务规则不满足"),
    CONFIRM_REQUIRED(42202, HttpStatus.UNPROCESSABLE_ENTITY, "需要确认后才能执行"),
    GROUPING_MISMATCH(42203, HttpStatus.UNPROCESSABLE_ENTITY, "球员不属于事件所属队分组"),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "触发限流"),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "服务端错误");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
