package com.yunhefc.contracts.registration;

/**
 * 报名状态编码；contracts 不能依赖报名域枚举，集中在此避免快照各自硬编码。
 */
final class RegistrationStatusCodes {

    static final int CONFIRMED = 0;

    private RegistrationStatusCodes() {
    }
}
