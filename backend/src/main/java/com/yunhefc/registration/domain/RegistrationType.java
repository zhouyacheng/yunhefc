package com.yunhefc.registration.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum RegistrationType implements CodedEnum {

    GUEST(0),
    PLAYER(1);

    @EnumValue
    private final int code;

    RegistrationType(int code) {
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

    public static RegistrationType of(int code) {
        for (RegistrationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知报名类型: " + code);
    }
}
