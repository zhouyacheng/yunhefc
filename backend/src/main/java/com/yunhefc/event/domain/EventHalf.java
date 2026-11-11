package com.yunhefc.event.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum EventHalf implements CodedEnum {

    FIRST(0),
    SECOND(1);

    @EnumValue
    private final int code;

    EventHalf(int code) {
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

    public static EventHalf of(int code) {
        for (EventHalf half : values()) {
            if (half.code == code) {
                return half;
            }
        }
        throw new IllegalArgumentException("未知半场: " + code);
    }
}
