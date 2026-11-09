package com.yunhefc.common.enums;

/**
 * 数据库 TINYINT 编码枚举的统一契约。
 */
public interface CodedEnum {

    int code();

    String label();
}
