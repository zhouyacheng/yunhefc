package com.yunhefc.auth.domain;

/**
 * 内置角色编码；自定义角色使用数据库中的其它 code。
 */
public final class RoleCodes {

    public static final String ADMIN = "ADMIN";
    public static final String PLAYER = "PLAYER";
    public static final String GUEST = "GUEST";

    private RoleCodes() {
    }
}
