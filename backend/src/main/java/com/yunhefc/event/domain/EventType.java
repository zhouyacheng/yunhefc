package com.yunhefc.event.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum EventType implements CodedEnum {

    GOAL(0),
    OWN_GOAL(1),
    SUBSTITUTION(2),
    YELLOW_CARD(3),
    RED_CARD(4);

    @EnumValue
    private final int code;

    EventType(int code) {
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

    public static EventType of(int code) {
        for (EventType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知事件类型: " + code);
    }
}
