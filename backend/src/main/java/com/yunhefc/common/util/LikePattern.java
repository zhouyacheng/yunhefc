package com.yunhefc.common.util;

/**
 * 把用户输入转成字面量 LIKE 模式；MySQL 默认以反斜杠转义。
 */
public final class LikePattern {

    private LikePattern() {
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
