package com.yunhefc.match.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum MatchType implements CodedEnum {

    OFFICIAL(0),
    INTERNAL(1),
    TRAINING(2),
    FRIENDLY(3);

    @EnumValue
    private final int code;

    MatchType(int code) {
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

    public static MatchType of(int code) {
        for (MatchType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知比赛类型: " + code);
    }
}
