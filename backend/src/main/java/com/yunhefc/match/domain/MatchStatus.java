package com.yunhefc.match.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum MatchStatus implements CodedEnum {

    SIGNUP_OPEN(0),
    SIGNUP_CLOSED(1),
    LOCKED(2),
    IN_PROGRESS(3),
    FINISHED(4),
    POSTPONED(5),
    CANCELLED(6);

    @EnumValue
    private final int code;

    MatchStatus(int code) {
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

    public static MatchStatus of(int code) {
        for (MatchStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知比赛状态: " + code);
    }
}
