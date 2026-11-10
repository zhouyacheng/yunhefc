package com.yunhefc.registration.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum RegistrationStatus implements CodedEnum {

    CONFIRMED(0),
    WAITLISTED(1),
    CANCELLED(2),
    REMOVED(3);

    @EnumValue
    private final int code;

    RegistrationStatus(int code) {
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

    public static RegistrationStatus of(int code) {
        for (RegistrationStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知报名状态: " + code);
    }
}
