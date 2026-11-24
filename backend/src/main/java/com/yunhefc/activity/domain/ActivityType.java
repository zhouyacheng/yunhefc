package com.yunhefc.activity.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum ActivityType implements CodedEnum {

    DINNER(0),
    ANNUAL_MEETING(1),
    OTHER(2);

    @EnumValue
    private final int code;

    ActivityType(int code) {
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

    public static ActivityType of(int code) {
        for (ActivityType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知活动类型: " + code);
    }
}
