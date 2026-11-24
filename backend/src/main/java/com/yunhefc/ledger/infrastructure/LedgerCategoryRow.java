package com.yunhefc.ledger.infrastructure;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LedgerCategoryRow {

    private Long id;
    private String name;
    private Integer direction;
    private Integer sortNo;
    private Boolean isActive;
    private Long entryCount;
}
