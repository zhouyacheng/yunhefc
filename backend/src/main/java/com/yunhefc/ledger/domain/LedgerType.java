package com.yunhefc.ledger.domain;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.yunhefc.common.enums.CodedEnum;

/**
 * 收支方向；分类 direction 为 NULL 时表示通用分类。
 */
public enum LedgerType implements CodedEnum {

    INCOME(0),
    EXPENSE(1);

    @EnumValue
    private final int code;

    LedgerType(int code) {
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

    public static LedgerType of(int code) {
        for (LedgerType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知收支方向: " + code);
    }
}
