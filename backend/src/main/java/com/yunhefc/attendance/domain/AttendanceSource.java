package com.yunhefc.attendance.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum AttendanceSource implements CodedEnum {

    SELF(0),
    ADMIN(1),
    SYSTEM(2);

    @EnumValue
    private final int code;

    AttendanceSource(int code) {
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

    public static AttendanceSource of(int code) {
        for (AttendanceSource source : values()) {
            if (source.code == code) {
                return source;
            }
        }
        throw new IllegalArgumentException("未知考勤来源: " + code);
    }
}
