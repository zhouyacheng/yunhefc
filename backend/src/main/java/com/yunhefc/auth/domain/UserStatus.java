package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum UserStatus implements CodedEnum {

    PENDING(0),
    ACTIVE(1),
    DISABLED(2);

    @EnumValue
    private final int code;

    UserStatus(int code) {
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

    public static UserStatus of(int code) {
        for (UserStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知用户状态: " + code);
    }
}
