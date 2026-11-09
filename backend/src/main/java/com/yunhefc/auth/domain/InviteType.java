package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum InviteType implements CodedEnum {

    TEAM(0),
    MATCH(1);

    @EnumValue
    private final int code;

    InviteType(int code) {
        this.code = code;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String label() {
        return name();
    }

    public static InviteType of(int code) {
        for (InviteType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知邀请类型: " + code);
    }
}
