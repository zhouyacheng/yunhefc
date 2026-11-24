package com.yunhefc.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class LedgerDomainTest {

    @Test
    void typeCodesAndLabels() {
        assertThat(LedgerType.of(0)).isEqualTo(LedgerType.INCOME);
        assertThat(LedgerType.of(1)).isEqualTo(LedgerType.EXPENSE);
        assertThat(LedgerType.INCOME.label()).isEqualTo("INCOME");
        assertThatThrownBy(() -> LedgerType.of(2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatesAmount() {
        assertThat(LedgerRules.amount(new BigDecimal("0.01"))).isEqualByComparingTo("0.01");
        assertThat(LedgerRules.amount(new BigDecimal("99999999.99")))
                .isEqualByComparingTo("99999999.99");
        // 响应固定两位小数：一位小数输入也按 300.00 输出。
        assertThat(LedgerRules.amount(new BigDecimal("300.0")).scale()).isEqualTo(2);

        for (String value : new String[]{"0", "-1", "100000000", "10.123"}) {
            assertThatThrownBy(() -> LedgerRules.amount(new BigDecimal(value)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        }
        assertThatThrownBy(() -> LedgerRules.amount(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void validatesDescriptionAndCategoryName() {
        assertThat(LedgerRules.description("  队费  ")).isEqualTo("队费");
        assertThatThrownBy(() -> LedgerRules.description("   "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LedgerRules.description("字".repeat(256)))
                .isInstanceOf(BusinessException.class);

        assertThat(LedgerRules.categoryName(" 球衣 ")).isEqualTo("球衣");
        assertThatThrownBy(() -> LedgerRules.categoryName(" "))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> LedgerRules.categoryName("分".repeat(33)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void directionMustMatchCategoryExceptUniversal() {
        assertThat(LedgerRules.categoryDirection(null)).isNull();
        assertThat(LedgerRules.categoryDirection(1)).isEqualTo(LedgerType.EXPENSE);
        assertThatThrownBy(() -> LedgerRules.categoryDirection(2))
                .isInstanceOf(BusinessException.class);

        LedgerCategory income = category(LedgerType.INCOME);
        LedgerCategory universal = category(null);
        LedgerRules.requireDirectionMatch(LedgerType.INCOME, income);
        LedgerRules.requireDirectionMatch(LedgerType.INCOME, universal);
        LedgerRules.requireDirectionMatch(LedgerType.EXPENSE, universal);

        assertThatThrownBy(() -> LedgerRules.requireDirectionMatch(LedgerType.EXPENSE, income))
                .isInstanceOf(BusinessException.class);
    }

    private static LedgerCategory category(LedgerType direction) {
        LedgerCategory category = new LedgerCategory();
        category.setDirection(direction);
        return category;
    }
}
