package com.yunhefc.common.security;

/**
 * 内容域权限码。集中定义，避免服务与通知处理器各写一份字面量。
 */
public final class ContentPermissions {

    public static final String DELETE_ANY = "content:delete:any";

    private ContentPermissions() {
    }
}
