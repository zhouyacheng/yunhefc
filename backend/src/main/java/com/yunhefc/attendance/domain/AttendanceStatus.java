package com.yunhefc.attendance.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum AttendanceStatus implements CodedEnum {

    PRESENT(0),
    ABSENT(1),
    TEMP_PRESENT(2);

    @EnumValue
    private final int code;

    AttendanceStatus(int code) {
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

    public static AttendanceStatus of(int code) {
        for (AttendanceStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知考勤状态: " + code);
    }
}
