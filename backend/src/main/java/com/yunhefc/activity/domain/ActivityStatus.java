package com.yunhefc.activity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum ActivityStatus implements CodedEnum {

    SIGNUP_OPEN(0),
    SIGNUP_CLOSED(1),
    FINISHED(2),
    CANCELLED(3);

    @EnumValue
    private final int code;

    ActivityStatus(int code) {
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

    public static ActivityStatus of(int code) {
        for (ActivityStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知活动状态: " + code);
    }
}
