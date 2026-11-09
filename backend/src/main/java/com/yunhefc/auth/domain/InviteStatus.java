package com.yunhefc.auth.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum InviteStatus implements CodedEnum {

    ACTIVE(0),
    REVOKED(1),
    EXPIRED(2);

    @EnumValue
    private final int code;

    InviteStatus(int code) {
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

    public static InviteStatus of(int code) {
        for (InviteStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知邀请状态: " + code);
    }
}
