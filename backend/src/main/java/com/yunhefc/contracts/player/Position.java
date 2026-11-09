package com.yunhefc.contracts.player;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum Position implements CodedEnum {

    GOALKEEPER(0),
    DEFENDER(1),
    MIDFIELDER(2),
    FORWARD(3);

    @EnumValue
    private final int code;

    Position(int code) {
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

    public static Position of(int code) {
        for (Position position : values()) {
            if (position.code == code) {
                return position;
            }
        }
        throw new IllegalArgumentException("未知位置: " + code);
    }
}
