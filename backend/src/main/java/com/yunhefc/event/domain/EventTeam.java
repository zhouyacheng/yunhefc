package com.yunhefc.event.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum EventTeam implements CodedEnum {

    HOME(0),
    AWAY(1);

    @EnumValue
    private final int code;

    EventTeam(int code) {
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

    public static EventTeam of(int code) {
        for (EventTeam team : values()) {
            if (team.code == code) {
                return team;
            }
        }
        throw new IllegalArgumentException("未知事件所属队: " + code);
    }
}
