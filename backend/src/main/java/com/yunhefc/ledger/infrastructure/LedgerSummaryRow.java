package com.yunhefc.ledger.infrastructure;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LedgerSummaryRow {

    private BigDecimal income;
    private BigDecimal expense;
}
