package com.yunhefc.ledger.infrastructure;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class LedgerTrendRow {

    private LocalDate bucketStart;
    private BigDecimal income;
    private BigDecimal expense;
}
