package com.yunhefc.contracts.settings;

import java.math.BigDecimal;

/**
 * 分组评分配置；与 system_setting 的 grouping.* 键同名。
 */
public record GroupingSettings(
        BigDecimal manualWeight,
        BigDecimal autoWeight,
        BigDecimal appearanceWeight,
        BigDecimal goalWeight,
        BigDecimal assistWeight,
        int minAppearances) {
}
