package com.yunhefc.notification.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

public enum NotificationType implements CodedEnum {

    REGISTRATION_CONFIRMED(0),
    WAITLIST_PROMOTED(1),
    MATCH_CREATED(2),
    MATCH_SIGNUP_CLOSED(3),
    MATCH_LOCKED(4),
    MATCH_STARTED(5),
    MATCH_FINISHED(6),
    MATCH_POSTPONED(7),
    MATCH_CANCELLED(8),
    ROLE_CHANGED(9),
    ACCOUNT_APPROVED(10),
    ACCOUNT_DISABLED(11);

    @EnumValue
    private final int code;

    NotificationType(int code) {
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

    public static NotificationType of(int code) {
        for (NotificationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知通知类型: " + code);
    }
}
