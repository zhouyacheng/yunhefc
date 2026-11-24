package com.yunhefc.activity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum SignupStatus implements CodedEnum {

    CONFIRMED(0),
    CANCELLED(1);

    @EnumValue
    private final int code;

    SignupStatus(int code) {
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
}
