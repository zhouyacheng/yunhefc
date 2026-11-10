package com.yunhefc.registration.domain;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;

/**
 * 分池名额规则：正式球员满额进候补，外挂球员满额直接拒绝；候补只对正式球员开放。
 */
public final class RegistrationPolicy {

    private RegistrationPolicy() {
    }

    public static RegistrationStatus decide(RegistrationType type, int confirmedInPool,
                                            int waitlistCount, int capacity,
                                            int guestCapacity, int waitlistCapacity) {
        if (type == RegistrationType.GUEST) {
            if (confirmedInPool >= guestCapacity) {
                throw new BusinessException(ErrorCode.CAPACITY_FULL, "外挂名额已满");
            }
            return RegistrationStatus.CONFIRMED;
        }
        if (confirmedInPool < capacity) {
            return RegistrationStatus.CONFIRMED;
        }
        if (waitlistCount < waitlistCapacity) {
            return RegistrationStatus.WAITLISTED;
        }
        throw new BusinessException(ErrorCode.CAPACITY_FULL, "正式名额与候补均已满");
    }
}
