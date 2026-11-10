package com.yunhefc.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class RegistrationPolicyTest {

    @Test
    void formalPlayerIsConfirmedWhileCapacityAvailable() {
        assertThat(RegistrationPolicy.decide(
                RegistrationType.PLAYER, 29, 0, 30, 4, 2))
                .isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void formalPlayerJoinsWaitlistWhenCapacityIsFull() {
        assertThat(RegistrationPolicy.decide(
                RegistrationType.PLAYER, 30, 1, 30, 4, 2))
                .isEqualTo(RegistrationStatus.WAITLISTED);
    }

    @Test
    void formalPlayerIsRejectedWhenCapacityAndWaitlistAreFull() {
        assertThatThrownBy(() -> RegistrationPolicy.decide(
                RegistrationType.PLAYER, 30, 2, 30, 4, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CAPACITY_FULL);
    }

    @Test
    void guestIsConfirmedWhileGuestCapacityAvailable() {
        assertThat(RegistrationPolicy.decide(
                RegistrationType.GUEST, 3, 0, 30, 4, 2))
                .isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void guestIsRejectedInsteadOfWaitlistedWhenGuestCapacityIsFull() {
        assertThatThrownBy(() -> RegistrationPolicy.decide(
                RegistrationType.GUEST, 4, 0, 30, 4, 2))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CAPACITY_FULL);
    }

    @Test
    void minimumWaitlistCapacityStillRejectsWhenFull() {
        assertThatThrownBy(() -> RegistrationPolicy.decide(
                RegistrationType.PLAYER, 30, 0, 30, 4, 0))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CAPACITY_FULL);
    }
}
