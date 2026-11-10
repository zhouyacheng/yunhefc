package com.yunhefc.match.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum OurSide implements CodedEnum {

    HOME(0),
    AWAY(1);

    @EnumValue
    private final int code;

    OurSide(int code) {
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

    public static OurSide of(int code) {
        for (OurSide side : values()) {
            if (side.code == code) {
                return side;
            }
        }
        throw new IllegalArgumentException("未知我方主客: " + code);
    }
}
