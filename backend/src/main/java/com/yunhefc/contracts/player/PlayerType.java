package com.yunhefc.contracts.player;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum PlayerType implements CodedEnum {

    GUEST(0),
    PLAYER(1);

    @EnumValue
    private final int code;

    PlayerType(int code) {
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

    public static PlayerType of(int code) {
        for (PlayerType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知球员类型: " + code);
    }
}
